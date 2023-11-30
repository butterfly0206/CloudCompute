# /usr/bin/python3

import asyncio
import config
import jmclient
import jmclient.model
import time
import typing
import worker_tests
from jmclient.exceptions import ApiException
from jmclient.model.array_job_args import ArrayJobArgs
from jmclient.model.array_job_item import ArrayJobItem
from junit_xml import TestCase, TestSuite


def is_response_failure(response):
    return isinstance(response, ApiException)


def ok_expected(response):
    return not is_response_failure(response)


def verify_bearer_token_same(response):
    return ok_expected(response) and response.token == config.BEARER_TOKEN


def verify_bearer_token_different(response):
    return ok_expected(response) and response.token != config.BEARER_TOKEN


def handle_response(response, test_case_name, start_time, acceptance_test):
    test_case = TestCase(name=test_case_name, elapsed_sec=time.time() - start_time)
    test_case.response = response
    test_case.status = "FAILED"
    if is_response_failure(response):
        test_case.stdout = ''
        msg = 'HTTP {0:d}: {1}'.format(response.status, response.body)
        test_case.stderr = 'HTTP {0:d}: {1}'.format(response.status, response.body)
        print('Test case {} failed: {}'.format(test_case_name, msg))
        test_case.add_failure_info(message=test_case.name + ' failed', output=test_case.stderr)
    elif acceptance_test(response):
        test_case.status = "SUCCESS"
        test_case.stdout = response
    return test_case


def execute_http(lambda_to_call, label, start_time, acceptance_test):
    try:
        result = lambda_to_call()
        return handle_response(result, label, start_time, acceptance_test)
    except ApiException as ex:
        return handle_response(ex, label, start_time, acceptance_test)


def get_full_api_url(api):
    return '{} {}{}'.format(api.operation.http_method, api.operation.swagger_spec.api_url, api.operation.path_name)


def post_array_job(test_name, start_time, service, worker, acceptance_test):
    jobs = []
    for key in worker_tests.TESTS:
        tags = [key]
        jobs.append(ArrayJobItem(tags=tags, payload=worker_tests.TESTS[key]))

    arrayJob = ArrayJobArgs(
        service=service,
        worker=worker,
        portfolio_version=config.PORTFOLIO_VERSION,
        jobs=jobs
    )

    return execute_http(lambda: config.OPENAPI_JM_CLIENT.create_jobs(job_args=arrayJob),
                        test_name, start_time, acceptance_test)


def get_array_job(test_name, start_time, job_id, acceptance_test):
    return execute_http(lambda: config.OPENAPI_JM_CLIENT.get_job(job_id),
                        test_name, start_time, acceptance_test)


def delete_array_job(test_name, start_time, job_id, acceptance_test):
    return execute_http(lambda: config.OPENAPI_JM_CLIENT.delete_job(job_id),
                        test_name, start_time, acceptance_test)


async def check_job_status(test_name, test_type, job_id, test_case):
    test_case.status = "FAILED"
    while True:
        await asyncio.sleep(3 * worker_tests.TESTS[test_type]["testDefinition"]["progressInterval"])
        print('Polling status of job {} for test {}'.format(job_id, test_name))
        try:
            job = config.OPENAPI_JM_CLIENT.get_job(job_id)
            if job.status == worker_tests.TESTS[test_type]["testDefinition"]["output"]["status"]:
                test_case.status = "SUCCESS"
                break
            elif job.status != "QUEUED" and job.status != "SCHEDULED" and job.status != "INPROGRESS":
                test_case.add_failure_info('Job({}: expecting status of {} and received {}'
                                           .format(job.job_id,
                                                   worker_tests.TESTS[test_type]["testDefinition"]["output"]["status"],
                                                   job.status))
                break
        except jmclient.ApiException as e:
            test_case.add_failure_info('Job({}: expecting status of {} and received {}'
                                       .format(job.job_id,
                                               worker_tests.TESTS[test_type]["testDefinition"]["output"]["status"],
                                               str(e.status)))
            break
    return test_case


def test_array_job_to_completion(test_cases):
    test_funcs = []
    service, workers = config.batch_service_and_workers_from_host(config.HOST)

    for worker in workers:
        test_name = "ArrayJobTest : post job : " + worker
        post_job_result = post_array_job(test_name, time.time(), service, worker, ok_expected)
        test_cases.append(post_job_result)

        if not is_response_failure(post_job_result.response):
            array_job = post_job_result.response
            print('Job successfully submitted for testName {}, job id = {}'.format(test_name, array_job.job_id))

            for job in array_job.jobs:
                test_type = job.tags[0]
                test_name = "ArrayJobTest:" + worker + ":" + test_type
                test_case = TestCase(name=test_name)
                try:
                    test_funcs.append(check_job_status(test_name, test_type, job.job_id, test_case))
                except asyncio.TimeoutError:
                    test_case.status = "FAILED"
                    test_case.add_failure_info(
                        'Test "{}" did not finish in {} seconds'.format(test_type, config.BATCH_TEST_TIMEOUT))

    return test_funcs


async def test_array_job_deletion(test_cases, array_job):
    test_name = "ArrayJobTest : delete array job : " + array_job.worker

    delete_job_result = delete_array_job(test_name, time.time(), array_job.job_id, ok_expected)
    test_cases.append(delete_job_result)

    # Deleting array job is asynchronous, wait few seconds before checking the status.
    await asyncio.sleep(15)

    get_array_job_result = get_array_job(test_name, time.time(), array_job.job_id, ok_expected)
    test_cases.append(get_array_job_result)

    test_case = TestCase(name=test_name)
    test_cases.append(test_case)
    array_job_result = get_array_job_result.response
    for job in array_job_result.jobs:
        if str(job.status) not in ["COMPLETED", "FAILED", "CANCELED"]:
            test_case.status = "FAILED"
            test_case.add_failure_info(
                'Test "{}", job {} was found in non terminal state {}'.format(test_case, job.job_id,
                                                                              job.status))

def test_post_and_delete_array_job(test_cases):
    test_funcs = []

    service, workers = config.batch_service_and_workers_from_host(config.HOST)
    for worker in workers:
        test_name = "ArrayJobTest : delete job : " + worker
        post_job_result = post_array_job(test_name, time.time(), service, worker, ok_expected)
        test_cases.append(post_job_result)

        # print('Job post result: {}'.format(post_job_result.response))

        if not is_response_failure(post_job_result.response):
            array_job = post_job_result.response
            test_funcs.append(test_array_job_deletion(test_cases, array_job))

    return test_funcs


def run_array_job_tests() -> typing.List[TestCase]:
    test_cases = []
    test_funcs = []
    test_funcs.extend(test_array_job_to_completion(test_cases))
    test_funcs.extend(test_post_and_delete_array_job(test_cases))

    event_loop = asyncio.get_event_loop()
    event_loop.set_debug(enabled=True)
    event_loop.run_until_complete(asyncio.gather(*test_funcs))

    return test_cases


def main() -> None:
    test_cases = run_array_job_tests()
    test_suite = TestSuite("CloudOS Compute Test Suite", test_cases)

    with open("./test-results.xml", 'w') as f:
        TestSuite.to_file(f, [test_suite], prettyprint=True)
    print(TestSuite.to_xml_string([test_suite]))
    return


if __name__ == "__main__":
    main()

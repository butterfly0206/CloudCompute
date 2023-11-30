# /usr/bin/python3

import asyncio
import json
import time
import typing

import bravado.exception
import config
import expectations
import jsonschema
import tests
from asgiref.sync import sync_to_async
from expectations import verify_bearer_token_different, verify_bearer_token_same
from junit_xml import TestCase, TestSuite

TESTS = {

    # Enable schema test back when JM adds input validation against schema

    # "testInvalidSchema": {"testDefinition": {"duration": 10, "progressInterval": 1, "progressUntil": 90,
    #                                      "heartbeatInterval": 2, "invalid": 10,
    #                                      "output": {"status": "FAILED"}}},

    "testBatchComplete": {"testDefinition": {"duration": 10, "progressInterval": 1, "progressUntil": 100,
                                        "heartbeatInterval": 2, "heartbeatUntil": 12,
                                        "output": {"status": "COMPLETED"}}},

    "testBatchFail": {"testDefinition": {"duration": 10, "progressInterval": 1, "progressUntil": 90,
                                    "heartbeatInterval": 2, "heartbeatUntil": 10,
                                    "output": {"status": "FAILED"}}},

    # This test will stop sending heartbeat after 2 seconds but will still succeed since progress is being sent
    # and progress is also counted as heartbeat.
    "testBatchNoHeartbeatComplete": {"testDefinition": {"duration": 10, "progressInterval": 1, "progressUntil": 100,
                                             "heartbeatInterval": 2, "heartbeatUntil": 2,
                                             "output": {"status": "COMPLETED"}}},

    "testBatchNoHeartbeatNoProgressFail": {"testDefinition": {"duration": 10, "progressInterval": 1, "progressUntil": 0,
                                         "heartbeatInterval": 2, "heartbeatUntil": 0,
                                         "output": {"status": "FAILED"}}},
    }


def post_job(testType,  start_time, service, worker, acceptance_test):
    JobArgs = config.JM_CLIENT.get_model('JobArgs')
    job = JobArgs(
        tags=['test', 'completion'],
        service=service,
        worker=worker,
        portfolioVersion=config.PORTFOLIO_VERSION,
        payload=TESTS[testType]
    )
    return tests.execute_http(lambda: config.JM_CLIENT.developers.createJob(jobArgs=job).result(),
     "Post Job", config.JM_CLIENT.developers.createJob, start_time, acceptance_test)


async def check_job_status(testName, testType, job_data, test_case):
    test_case.status = "FAILED"
    while True:
        await asyncio.sleep(TESTS[testType]["testDefinition"]["progressInterval"] * 3)
        print('Polling status of job {} for test {}'.format(job_data['jobID'], testName))
        response = await sync_to_async(config.JM_CLIENT.developers.getJob)(id=job_data['jobID'])
        try:
            result_item = await sync_to_async(response.result)()
            result = result_item[0]
            if result["status"] == TESTS[testType]["testDefinition"]["output"]["status"]:
                test_case.status = "SUCCESS"
                break
            elif result["status"] != "QUEUED" and result["status"] != "CREATED" and result["status"] != "SCHEDULED" and result["status"] != "INPROGRESS":
                test_case.add_failure_info('Job({}: expecting status of {} and received {}'
                                        .format(job_data['jobID'], TESTS[testType]["testDefinition"]["output"]["status"], result["status"]))
                break
        except bravado.exception.HTTPError as e:
            response = getattr(e, 'response', None)
            test_case.add_failure_info('Job({}: expecting status of {} and received {}'
                                    .format(job_data['jobID'], TESTS[testType]["testDefinition"]["output"]["status"], str(response.status_code)))
            break
        except jsonschema.exceptions.ValidationError as e:
            test_case.add_failure_info('Job {} ({}): got json schema validation error from polling the job status: {}'.format(
                job_data['jobID'], testType, str(e)))
            break


async def run_test(testType, start_time, service, worker, first_one) -> typing.List[TestCase]:
    test_cases = []
    test_name = worker + ":" + testType
    test_case = TestCase(name=test_name, elapsed_sec=time.time() - start_time)
    test_cases.append(test_case)

    post_job_result = await sync_to_async(post_job)(testType, start_time, service, worker,
                                                    expectations.post_job_2xx_expected)
    print('Job post result: HTTP status {:d} with body {:s}'.format(
        post_job_result.response.status_code, post_job_result.response.text))

    job_data = json.loads(post_job_result.response.text)
    # Call the getToken and refreshToken APIs if apigee is enabled
    if config.USE_APIGEE and first_one:
        time.sleep(3)  # Sleep for 3 seconds to give the job time to become active
        test_cases.append(tests.execute_http(lambda: config.WM_CLIENT.token.getToken(
            jobID=job_data['jobID'], jobSecret=job_data['jobID'], refresh=False).result(),
                                             "Get job bearer token for batch job",
                                             config.WM_CLIENT.token.getToken,
                                             start_time, verify_bearer_token_same))
        # extend token is not supported in FedRAMP end-points yet, disable this test for cosv3 until it becomes available
        # https://autodesk.slack.com/archives/C075EFGET/p1649874213798149
        if not config.IS_COSV3:
            test_cases.append(tests.execute_http(lambda: config.WM_CLIENT.token.getToken(
                jobID=job_data['jobID'], jobSecret=job_data['jobID'], refresh=True).result(),
                                                 "Refresh job bearer token for batch job",
                                                 config.WM_CLIENT.token.getToken,
                                                 start_time, verify_bearer_token_different))

    tests.add_job_defaults(job_data)
    if post_job_result.response.status_code == 200 or post_job_result.response.status_code == 202:
        print('Job successfully submitted for testType {}, job id = {}'.format(testType, job_data['jobID']))
    try:
        await asyncio.wait_for(check_job_status(test_name, testType, job_data, test_case), config.BATCH_TEST_TIMEOUT)
    except asyncio.TimeoutError:
        test_case.status = "FAILED"
        test_case.add_failure_info('Test "{}" did not finish in {} seconds'.format(testType, config.BATCH_TEST_TIMEOUT))

    test_case.elapsed_sec = time.time() - start_time
    return test_cases


def run_batch_tests():
    test_funcs = []
    service, workers = config.batch_service_and_workers_from_host(config.HOST)

    for key in TESTS:
        for worker in workers:
            first_one = (worker == workers[0]) and (key == list(TESTS.keys())[0])
            if first_one:
                print(f'{worker} is first one, USE_APIGEE={config.USE_APIGEE}')
            test_funcs.append(run_test(key, time.time(), service, worker, first_one))

    event_loop = asyncio.get_event_loop()
    event_loop.set_debug(enabled=True)
    list_of_lists_of_test_cases = event_loop.run_until_complete(asyncio.gather(*test_funcs))
    test_cases = [item for one_list in list_of_lists_of_test_cases for item in one_list]

    return test_cases


def main() -> None:
    test_cases = run_batch_tests()
    test_suite = TestSuite("CloudOS Compute Test Suite", test_cases)

    with open("./test-results.xml", 'w') as f:
        TestSuite.to_file(f, [test_suite], prettyprint=True)
    print(TestSuite.to_xml_string([test_suite]))
    return


if __name__ == "__main__":
    main()

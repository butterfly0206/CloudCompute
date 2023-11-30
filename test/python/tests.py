#! /usr/bin/env python3
import json
import sys
import time
import uuid

import array_job_tests
import bravado.exception
import config
import worker_tests
from expectations import *
from junit_xml import TestSuite, TestCase


def handle_response(response, test_case_name, start_time, acceptance_test):
    test_case = TestCase(name=test_case_name, elapsed_sec=time.time() - start_time)
    test_case.response = response
    test_case.status = "FAILED"
    if not response:
        test_case.stdout = ''
        test_case.stderr = 'Connection failed to server, no response received!'
        test_case.add_failure_info(message=test_case.name + ' failed', output=test_case.stderr)
        print('Test case {} failed: no response from server'.format(test_case_name))
    elif (not acceptance_test(response)):
        test_case.stdout = ''
        msg = 'HTTP {0:d}: {1}'.format(response.status_code, response.text)
        test_case.stderr = 'HTTP {0:d}: {1}'.format(response.status_code, response.text)
        print('Test case {} failed: {}'.format(test_case_name, msg))
        test_case.add_failure_info(message=test_case.name + ' failed', output=test_case.stderr)
    else:
        test_case.status = "SUCCESS"
        test_case.stdout = 'HTTP {0:d}: {1}'.format(response.status_code, response.text)
    return test_case

def execute_http(lambda_to_call, label, swagger_operation, start_time, acceptance_test):
    try:
        print('Calling {} from {}'.format(get_full_api_url(swagger_operation), label))
        result = lambda_to_call()
        return handle_response(result[1], label, start_time, acceptance_test)
    except bravado.exception.HTTPError as e:
        response = getattr(e, 'response', None)
        return handle_response(response, label, start_time, acceptance_test)

def job_manager_health_test(start_time, acceptance_test):
    return execute_http(lambda: config.JM_CLIENT.admins.healthcheckGet().result(),
                        "Job Manager Health",
                        config.JM_CLIENT.admins.healthcheckGet,
                        start_time, acceptance_test)


def worker_manager_health_test(start_time, acceptance_test):
    return execute_http(lambda: config.WM_CLIENT.healthcheck.healthcheckGet().result(),
                        "Worker Manager health",
                        config.WM_CLIENT.healthcheck.healthcheckGet,
                        start_time, acceptance_test)

def get_job_oxygen_token(start_time):
    test_cases = []
    if not (config.USE_APIGEE or config.FORCE_BEARER_TOKEN):
        print('Skipping oxygen token tests because not using Apigee or no BearerToken in environment')
        return test_cases
    post_job_result = post_job_with_portfolio(start_time, "Poll in get job oxygen token", post_job_2xx_expected)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    poll_job_result = poll_job(start_time, config.test_service_and_worker_from_host(config.HOST),
                               config.PORTFOLIO_VERSION, "Poll in get job oxygen token", ok_expected)
    worker_job_data = json.loads(poll_job_result.response.text)
    add_worker_defaults(worker_job_data)
    test_cases.append(execute_http(lambda: config.WM_CLIENT.token.getToken(
        jobID=job_data['jobID'], jobSecret=worker_job_data['jobSecret'], refresh=False).result(),
                                   "Get job bearer token",
                                   config.WM_CLIENT.token.getToken,
                                   start_time, verify_bearer_token_same))
    # extend token is not supported by FedRAMP end-points due to compliance reason,
    # disable this for FedRAMP until/IF it becomes available
    # https://autodesk.slack.com/archives/C075EFGET/p1649874213798149
    if not config.IS_FEDRAMP:
        test_cases.append(execute_http(lambda: config.WM_CLIENT.token.getToken(
            jobID=job_data['jobID'], jobSecret=worker_job_data['jobSecret'], refresh=True).result(),
                                       "Refresh job bearer token", config.WM_CLIENT.token.getToken,
                                       start_time, verify_bearer_token_different))
    test_cases.append(delete_job(start_time, job_data['jobID'], 'Delete valid job', ok_expected))
    return test_cases


def do_post_job(start_time, service, worker, portfolioVersion, idempotencyId, description, acceptance_test):
    JobArgs = config.JM_CLIENT.get_model('JobArgs')
    jobArgs = JobArgs(
        tags=['simulation', 'bicycle'],
        service=service,
        portfolioVersion=portfolioVersion,
        idempotencyId=idempotencyId,
        worker=worker,
        payload={
            "inputFilePath": "https://developer-dev.api.autodesk.com/oss/v2/buckets/longstestbucket1234/objects/mytestfile01.txt",
            "description": description
        }
    )
    return do_post_job_with_client(start_time, jobArgs, description, config.JM_CLIENT, acceptance_test)


def do_post_job_with_client(start_time, jobArgs, description, client, acceptance_test):
    return execute_http(
        lambda: client.developers.createJob(jobArgs=jobArgs, noBatch=True).result(),
        description, client.developers.createJob, start_time, acceptance_test)


def post_job_with_portfolio(start_time, description, acceptance_test):
    service, worker = config.test_service_and_worker_from_host(config.HOST)
    return do_post_job(start_time, service, worker, config.PORTFOLIO_VERSION, None, description, acceptance_test)


def post_job_with_idempotency_id(start_time, idempotency_id, description, acceptance_test):
    service, worker = config.test_service_and_worker_from_host(config.HOST)
    return do_post_job(start_time, service, worker, config.PORTFOLIO_VERSION, idempotency_id, description,
                       acceptance_test)


def post_job_bad_service(start_time, acceptance_test):
    _, worker = config.test_service_and_worker_from_host(config.HOST)
    return do_post_job(start_time, 'this-is-a-bad-service', worker, config.PORTFOLIO_VERSION, None,
                       "Post Job With Invalid Service", acceptance_test)


def post_job_bad_worker(start_time, acceptance_test):
    service, _ = config.test_service_and_worker_from_host(config.HOST)
    return do_post_job(start_time, service, 'this-is-a-bad-worker', config.PORTFOLIO_VERSION, None,
                       "Post Job With Invalid Worker", acceptance_test)


def post_job_bad_portfolio(start_time, acceptance_test):
    service, worker = config.test_service_and_worker_from_host(config.HOST)
    return do_post_job(start_time, service, worker, '9.99.999', None, "Post Job With Invalid Portfolio",
                       acceptance_test)


def post_job_missing_args(start_time, acceptance_test):
    JobArgs = config.JM_CLIENT.get_model('JobArgs')
    jobArgs = JobArgs(
        service="",
        worker="",
        tags=['simulation', 'bicycle']
    )
    return do_post_job_with_client(start_time, jobArgs, "Post Job Missing Args", config.JM_CLIENT, acceptance_test)


def post_job_bad_tags(start_time, acceptance_test):
    service, worker = config.test_service_and_worker_from_host(config.HOST)
    client = config.JM_CLIENT_INVALID_TAGS
    JobArgs = client.get_model('JobArgs')
    jobArgs = JobArgs(
        service=service,
        worker=worker,
        portfolioVersion=config.PORTFOLIO_VERSION,
        tags="not-an-array"
    )
    return do_post_job_with_client(start_time, jobArgs, "Post Job With Invalid Tags", client,
                                   acceptance_test)


def get_job(start_time, job_data, description, acceptance_test):
    return execute_http(
        lambda: config.JM_CLIENT.developers.getJob(id=job_data['jobID']).result(),
        description,
        config.JM_CLIENT.developers.getJob,
        start_time,
        acceptance_test)


def complete_invalid_job(start_time):
    Conclusion = config.WM_CLIENT.get_model('Conclusion')
    conclusion = Conclusion(
        jobID='abcdabcd-abcd-abcd-abcd-abcdabcdabcdabcd',
        jobSecret='InvalidJobSecret',
        status='COMPLETED',
        result={'message': 'Oh sure.'}
    )
    return complete_job(start_time, conclusion, "Complete non-existent job", failure_expected)

def complete_job_invalid_token(start_time, job_data, description):
    Conclusion = config.WM_CLIENT.get_model('Conclusion')
    conclusion = Conclusion(
        jobID=job_data['jobID'],
        jobSecret=job_data.get('jobSecret', 'VGhpcyBpcyB0ZXh0Lg=='),
        status='COMPLETED',
        result={'message': 'This was never picked up by a worker'}
    )
    return complete_job(start_time, conclusion, description, failure_expected)

def complete_job_valid_token(start_time, worker_job_data, description):
    conclusion = make_conclusion(
        jobID=worker_job_data['jobID'],
        jobSecret=worker_job_data['jobSecret'],
        status='COMPLETED',
        result=expected_results()
    )
    return complete_job(start_time, conclusion, description, ok_expected)

def complete_job_with_error(start_time, worker_job_data, description):
    conclusion = make_conclusion(
        jobID=worker_job_data['jobID'],
        jobSecret=worker_job_data['jobSecret'],
        status='FAILED',
        result=expected_results(),
        details=expected_details(),
        error=expected_error()
    )
    return complete_job(start_time, conclusion, description, ok_expected)

def make_conclusion(jobID, jobSecret, status, result, details=None, error=None):
    Conclusion = config.WM_CLIENT.get_model('Conclusion')
    return Conclusion(jobID=jobID, jobSecret=jobSecret, status=status, result=result, details=details, error=error)

def complete_job_already_completed(start_time, worker_job_data, description):
    conclusion = make_conclusion(
        jobID=worker_job_data['jobID'],
        jobSecret=worker_job_data['jobSecret'],
        status='COMPLETED',
        result={'message': 'Completed a second time?'}
    )
    return complete_job(start_time, conclusion, description, failure_expected)

def complete_job(start_time, conclusion, description, acceptance_test):
    headers = config.WM_AUTH_HEADER
    return execute_http(lambda: config.WM_CLIENT.complete.postComplete(
        data=conclusion, _request_options={
                    'headers': headers,
                }).result(),
        description, config.WM_CLIENT.complete.postComplete, start_time, acceptance_test)

def delete_job(start_time, job_id, description, acceptance_test):
    return execute_http(lambda: config.JM_CLIENT.developers.deleteJob(
        id=job_id).result(), description, config.JM_CLIENT.developers.deleteJob, start_time, acceptance_test)

def poll_job(start_time, service_and_worker, portfolioVersion, description, acceptance_test):
    service, worker = service_and_worker
    PollData = config.WM_CLIENT.get_model('PollForJob')
    headers = config.WM_AUTH_HEADER
    pollData = PollData(service=service, worker=worker, portfolioVersion=portfolioVersion)
    return execute_http(lambda: config.WM_CLIENT.poll.pollForJob(pollData=pollData,
                _request_options={
                    'headers': headers,
                    'timeout': 70
                }).result(), description,
                config.WM_CLIENT.poll.pollForJob,
                start_time, acceptance_test)

def get_full_api_url(api):
    return '{} {}{}'.format(api.operation.http_method, api.operation.swagger_spec.api_url, api.operation.path_name)

def heartbeat_job(start_time, job_data, description, acceptance_test):
    Heartbeat = config.WM_CLIENT.get_model('Heartbeat')
    headers = config.WM_AUTH_HEADER
    heartbeat = Heartbeat(jobID=job_data['jobID'], jobSecret=job_data['jobSecret'])
    return execute_http(lambda: config.WM_CLIENT.heartbeat.postHeartbeat(data=heartbeat, _request_options={
                    'headers' : headers
                }).result(), description, config.WM_CLIENT.heartbeat.postHeartbeat, start_time, acceptance_test)

def heartbeat_job_new_auth(start_time, job_data, description, acceptance_test):
    Heartbeat = config.WM_NEW_AUTH.get_model('Heartbeat')
    headers = config.WM_TEST_AUTH_HEADER
    heartbeat = Heartbeat(jobID=job_data['jobID'], jobSecret=job_data['jobSecret'])
    return execute_http(lambda: config.WM_NEW_AUTH.heartbeat.postHeartbeat(data=heartbeat, _request_options={
                    'headers' : headers
                }).result(),
        description, config.WM_NEW_AUTH.heartbeat.postHeartbeat, start_time, acceptance_test)

def workflow_for_killing_job_in_progress(start_time):
    test_cases = []
    test_cases.extend(search_and_cancel_jobs(time.time(), "Search and cancel all jobs"))
    post_job_result = post_job_with_portfolio(start_time,  "Poll in killing job in progress", post_job_2xx_expected)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    poll_job_result = poll_job(start_time, config.test_service_and_worker_from_host(config.HOST), config.PORTFOLIO_VERSION, "Poll in killing job in progress", ok_expected)
    worker_job_data = json.loads(poll_job_result.response.text)
    add_worker_defaults(worker_job_data)
    delete_job(start_time, worker_job_data['jobID'], "Delete valid job", ok_expected)
    test_cases.append(heartbeat_job(start_time, worker_job_data, "Heartbeat job after deleted", heartbeat_canceled_expected))
    test_cases.append(complete_job(start_time, make_conclusion(
        worker_job_data['jobID'], worker_job_data['jobSecret'], 'COMPLETED', { 'hello': 'world' }), "Complete deleted job", failure_expected))
    return test_cases

def add_worker_defaults(worker_job_data):
    worker_job_data.setdefault('jobID', '00000000-0000-0000-0000-000000000000')
    worker_job_data.setdefault('jobSecret', 'no-job-secret-found')

def add_job_defaults(job_data):
    job_data.setdefault('jobID', '00000000-0000-0000-0000-000000000000')

def workflow_for_job_with_portfolio_version(start_time):
    test_cases = []
    post_job_result = post_job_with_portfolio(start_time, "Workflow with portfolio version poll", post_job_2xx_expected)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    poll_job_result = poll_job(start_time, config.test_service_and_worker_from_host(config.HOST), config.PORTFOLIO_VERSION, "Workflow with portfolio version poll", ok_expected)
    worker_job_data = json.loads(poll_job_result.response.text)
    add_worker_defaults(worker_job_data)
    test_cases.append(complete_job_valid_token(time.time(), worker_job_data, "Complete a requested job with a portfolio version"))
    return test_cases


def test_deleting_a_deleted_job(start_time):
    test_cases = []
    post_job_result = post_job_with_portfolio(start_time, "Delete Valid Job", post_job_2xx_expected)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    test_cases.append(delete_job(time.time(), job_data['jobID'], "Delete Valid Job", ok_expected))
    test_cases.append(delete_job(time.time(), job_data['jobID'], "Delete previously deleted Job", failure_expected))
    return test_cases


def add_tag(start_time, job_id, tag_name, description, acceptance_test):
    return execute_http(lambda: config.JM_CLIENT.developers.addTag(
        jobId=job_id, tagName=tag_name, _request_options={
            'headers': {"Content-Type": "application/x-www-form-urlencoded"},
            'timeout': 70
        }).result(), description, config.JM_CLIENT.developers.addTag, start_time,
                        acceptance_test)


def delete_tag(start_time, job_id, tag_name, description, acceptance_test):
    return execute_http(lambda: config.JM_CLIENT.developers.deleteTag(
        jobId=job_id, tagName=tag_name).result(), description, config.JM_CLIENT.developers.deleteTag, start_time,
                        acceptance_test)


def test_add_job_tag(start_time):
    test_cases = []
    post_job_result = post_job_with_portfolio(start_time, "Add tag to job", post_job_2xx_expected)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    test_cases.append(add_tag(time.time(), job_data['jobID'], "test_tag", "Add a tag to job", ok_expected))
    test_cases.append(add_tag(time.time(), job_data['jobID'], "test_tag", "Add the same tag", not_modified_expected))
    test_cases.append(add_tag(time.time(), "non-existing-job-id", "test_tag", "Add tag to a non-existing job", failure_expected))
    return test_cases


def test_delete_job_tag(start_time):
    test_cases = []
    post_job_result = post_job_with_portfolio(start_time, "Delete job tag", post_job_2xx_expected)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    test_cases.append(delete_tag(time.time(), job_data['jobID'], "simulation", "delete existing job tag", ok_expected))
    test_cases.append(delete_tag(time.time(), job_data['jobID'], "simulation", "delete non-existing job tag", not_modified_expected))
    test_cases.append(delete_tag(time.time(), "non-existing-job-id", "simulation", "delete tag from a non-existing job ", failure_expected))
    return test_cases


def test_creating_job_with_same_idempotencyId(start_time):
    test_cases = []
    idempotency_id = str(uuid.uuid4())
    post_job_result = post_job_with_idempotency_id(start_time, idempotency_id,
                                                   "Create Job with idempotencyId with success", post_job_2xx_expected)
    test_cases.append(post_job_result)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    poll_job_result = poll_job(start_time, config.test_service_and_worker_from_host(config.HOST),
                               config.PORTFOLIO_VERSION, "Workflow with idempotencyId poll", ok_expected)
    worker_job_data = json.loads(poll_job_result.response.text)
    add_worker_defaults(worker_job_data)
    test_cases.append(
        complete_job_valid_token(time.time(), worker_job_data, "Complete a requested job with a idempotencyId"))
    test_cases.append(
        post_job_with_idempotency_id(start_time, idempotency_id, "Create Job with the same idempotencyId with and fail",
                                     failure_expected))
    return test_cases

def return_this(ret):
    return ret

def search_jobs(start_time, description, acceptance_test):
    service, _ = config.test_service_and_worker_from_host(config.HOST)
    return execute_http(lambda: config.JM_CLIENT.developers.searchRecentJobs(
        service=service,
        maxResults=None,
        fromTime=None,
        toTime=None,
        tag=None,
        nextToken=None).result(),
                        description, config.JM_CLIENT.developers.searchRecentJobs, start_time, acceptance_test)

def search_jobs_invalid_service(start_time, description, acceptance_test):
    service = 'thisIsAnInvalidService'
    return execute_http(lambda: config.JM_CLIENT.developers.searchRecentJobs(
        service=service,
        maxResults=None,
        fromTime=None,
        toTime=None,
        tag=None,
        nextToken=None).result(),
                        description, config.JM_CLIENT.developers.searchRecentJobs, start_time, acceptance_test)

def search_and_cancel_jobs(start_time, description):
    test_cases = []
    service, _ = config.test_service_and_worker_from_host(config.HOST)
    search_tuple = config.JM_CLIENT.developers.searchRecentJobs(
        service=service,
        maxResults=100,
        fromTime=None,
        toTime=None,
        tag=None,
        nextToken=None).result()
    search_result = search_tuple[0]
    test_cases.append(
        execute_http(lambda: return_this(search_tuple),
                     description, config.JM_CLIENT.developers.searchRecentJobs, start_time, ok_expected))
    for job in search_result.jobs:
        if job.status not in ["COMPLETED", "CANCELED", "FAILED", "TIMEDOUT"]:
            print('Canceling job id ' + job.jobID + ' which was previously in the ' + job.status + ' state')
            test_cases.append(delete_job(start_time, job.jobID, "Deleting active job", ok_expected))
    return test_cases

def search_jobs_with_paging(start_time, description):
    test_cases = []
    service, _ = config.test_service_and_worker_from_host(config.HOST)
    search_tuple = config.JM_CLIENT.developers.searchRecentJobs(
        service=service,
        maxResults=1,
        fromTime=None,
        toTime=None,
        tag=None,
        nextToken=None).result()
    search_result = search_tuple[0]
    test_cases.append(
        execute_http(lambda: return_this(search_tuple),
                     description + ' (1 of 2)', config.JM_CLIENT.developers.searchRecentJobs, start_time, search_results_one_ok_expected))
    test_cases.append(execute_http(lambda: config.JM_CLIENT.developers.searchRecentJobs(
        service=service,
        maxResults=1,
        fromTime=None,
        toTime=None,
        tag=None,
        nextToken=search_result.nextToken).result(),
                                   description + ' (2 of 2)', config.JM_CLIENT.developers.searchRecentJobs, start_time, search_results_one_ok_expected))
    return test_cases

def search_jobs_with_invalid_paging(start_time, description, acceptance_test):
    service, _ = config.test_service_and_worker_from_host(config.HOST)
    badNextToken='NOT_A_NEXT_TOKEN'
    return execute_http(lambda: config.JM_CLIENT.developers.searchRecentJobs(
        service=service,
        maxResults=1,
        fromTime=None,
        toTime=None,
        tag=None,
        nextToken=badNextToken).result(),
                        description, config.JM_CLIENT.developers.searchRecentJobs, start_time, acceptance_test)

def workflow_for_search(start_time):
    test_cases = []
    test_cases.extend(search_and_cancel_jobs(start_time, "Search and delete all jobs"))
    test_cases.append(post_job_with_portfolio(start_time, "Search recent jobs with paging 1", post_job_2xx_expected))
    test_cases.append(post_job_with_portfolio(start_time, "Search recent jobs with paging 2", post_job_2xx_expected))
    test_cases.append(search_jobs(start_time, "Search recent jobs", search_results_some_ok_expected))
    # This test case cannot be used when making the API calls via Apigee, because the ServiceId specified is
    # not used - the client id from the OAuth token is used instead.
    test_cases.extend(search_jobs_with_paging(start_time, "Search recent jobs with paging"))
    if not config.USE_APIGEE:
        test_cases.append(search_jobs_invalid_service(start_time, "Search recent jobs with invalid service", search_results_none_ok_expected))
    test_cases.append(search_jobs_with_invalid_paging(start_time, "Search recent jobs with invalid nextToken", bad_request_expected))
    test_cases.extend(search_and_cancel_jobs(start_time, "Search and delete all jobs"))
    return test_cases

def progress_job(start_time, job_data, details, percent, description, acceptance_test):
    Progress = config.WM_CLIENT.get_model('Progress')
    headers = config.WM_AUTH_HEADER
    progress = Progress(jobID=job_data['jobID'], jobSecret=job_data['jobSecret'], details=details, percent=percent)
    return execute_http(lambda: config.WM_CLIENT.progress.postProgress(progress_data=progress, _request_options={
                    'headers' : headers,
                }).result(), description, config.WM_CLIENT.progress.postProgress, start_time, acceptance_test)

def workflow_for_progress(start_time):
    test_cases = []
    test_cases.extend(search_and_cancel_jobs(start_time, "Search and delete all jobs"))
    post_job_result = post_job_with_portfolio(start_time, "Poll for progress workflow", post_job_2xx_expected)
    test_cases.append(post_job_result)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    poll_job_result = poll_job(start_time, config.test_service_and_worker_from_host(config.HOST), config.PORTFOLIO_VERSION, "Poll for progress workflow", ok_expected)
    test_cases.append(poll_job_result)
    worker_job_data = json.loads(poll_job_result.response.text)
    add_worker_defaults(worker_job_data)
    test_cases.append(progress_job(start_time, worker_job_data, expected_progress_details(), 50, 'Set progress on active job', ok_expected))
    test_cases.append(get_job(start_time, worker_job_data, "Get Job for progress details", progress_details_half_ok_expected))
    test_cases.append(progress_job_different_auth(start_time, worker_job_data, expected_progress_details(), 70,
                                                  'Set progress on active job', not_authorized_expected))
    test_cases.append(complete_job_with_error(time.time(), worker_job_data, "Complete a requested job with error"))
    test_cases.append(get_job(start_time, worker_job_data, "Failed jobs have no results", ok_expected_no_results))
    print('Sleeping for 2.0 seconds before canceling jobs')
    time.sleep(2.0)
    test_cases.extend(search_and_cancel_jobs(start_time, "Search and delete all jobs"))
    return test_cases

def workflow_for_anomalous_polling(start_time):
    test_cases = []
    service, worker = config.test_service_and_worker_from_host(config.HOST)
    good_service_good_worker = (service, worker)
    bad_service_good_worker = ('this-is-a-bad-service', worker)
    good_service_bad_worker = (service, 'this-is-a-bad-worker')
    test_cases.append(poll_job(start_time, bad_service_good_worker, config.PORTFOLIO_VERSION, "Poll with invalid service", failure_expected))
    test_cases.append(poll_job(start_time, good_service_good_worker, '9.9.9999', "Poll with invalid portfolio", failure_expected))
    test_cases.append(poll_job(start_time, good_service_bad_worker, config.PORTFOLIO_VERSION, "Poll with invalid worker", failure_expected))
    return test_cases

def workflow_for_anomalous_creating(start_time):
    if config.USE_APIGEE:
        return []
    else:
        return [post_job_bad_service(start_time, failure_expected),
                post_job_bad_portfolio(start_time, failure_expected),
                post_job_bad_worker(start_time, failure_expected),
                post_job_bad_tags(start_time, bad_request_expected)]

# Properly formated but junk job data
def invalid_job_data():
    return {
            'jobID': 'abcdabcd-abcd-abcd-abcd-abcdabcdabcd',
            'jobSecret': 'BOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOGUSBOG'
            }

def poll_job_different_auth(start_time, service_and_worker, portfolioVersion, description, acceptance_test):
    # WM_NEW_AUTH is a new WM client that uses credentials for Test Moniker, to poll for a job of App Moniker
    service, worker = service_and_worker
    PollData = config.WM_NEW_AUTH.get_model('PollForJob')
    headers = config.WM_TEST_AUTH_HEADER
    pollData = PollData(service=service, worker=worker, portfolioVersion=portfolioVersion)
    return execute_http(lambda: config.WM_NEW_AUTH.poll.pollForJob(pollData=pollData,
                _request_options={
                    'timeout': 70,
                    'headers' : headers
                }).result(), description,
                config.WM_NEW_AUTH.poll.pollForJob,
                start_time, acceptance_test)

def workflow_for_impersonation_polling(start_time):
    test_cases = []
    #Create a new WM Client with a different COS_WORKER_SECRET
    #Use the new WM client to poll for job of the original service and worker
    #It should give 401, not authorized exception.
    test_cases.append(poll_job_different_auth(start_time, config.test_service_and_worker_from_host(config.HOST),
                                              config.PORTFOLIO_VERSION,
                                              "Poll using security context of another moniker ",
                                              not_authorized_expected))
    return test_cases


def progress_job_different_auth(start_time, job_data, details, percent, description, acceptance_test):
    Progress = config.WM_NEW_AUTH.get_model('Progress')
    headers = config.WM_TEST_AUTH_HEADER
    progress = Progress(jobID=job_data['jobID'], jobSecret=job_data['jobSecret'], details=details, percent=percent)
    return execute_http(lambda: config.WM_NEW_AUTH.progress.postProgress(progress_data=progress, _request_options={
        'headers': headers,
    }).result(), description, config.WM_NEW_AUTH.progress.postProgress, start_time, acceptance_test)


def workflow_for_unresponsive_worker(start_time):
    test_cases = []
    # we only run this in sandbox since that's where we define the 'loadtestw' worker
    if not config.IS_SANDBOX:
        return test_cases

    # 1 Post a job with no retries (so, like 3)
    # 2 Poll for the job, and let it time out, it ostensibly get's re-queued
    #   Repeat step 2 until (gray box testing) we know it's not going to be retried anymore
    # 3 Verify the job status is FAILED

    test_cases.extend(search_and_cancel_jobs(start_time, "Search and delete all jobs"))
    service, _ = config.test_service_and_worker_from_host(config.HOST)
    worker = "loadtestw"
    heartbeat_timeout = 5  # defined in fpccomp-app
    post_job_result = do_post_job(start_time, service, worker, config.PORTFOLIO_VERSION, None,
                                  "Post for unresponsive worker workflow",
                                  post_job_2xx_expected)
    test_cases.append(post_job_result)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    # Default tries for this is 5, so we need to poll 6 times to get to a failure
    poll_job_result = poll_job(start_time, [service, worker], config.PORTFOLIO_VERSION,
                               "Poll for unresponsive worker workflow, 1", ok_expected)
    worker_job_data = json.loads(poll_job_result.response.text)
    test_cases.append(poll_job_result)
    time.sleep(heartbeat_timeout + 2)  # should heartbeat timeout
    poll_job_result = poll_job(start_time, [service, worker], config.PORTFOLIO_VERSION,
                               "Poll for unresponsive worker workflow, 2", ok_expected)
    test_cases.append(poll_job_result)
    time.sleep(heartbeat_timeout + 2)  # should heartbeat timeout
    poll_job_result = poll_job(start_time, [service, worker], config.PORTFOLIO_VERSION,
                               "Poll for unresponsive worker workflow, 3", ok_expected)
    test_cases.append(poll_job_result)
    time.sleep(heartbeat_timeout + 2)  # should heartbeat timeout
    poll_job_result = poll_job(start_time, [service, worker], config.PORTFOLIO_VERSION,
                               "Poll for unresponsive worker workflow, 4", ok_expected)
    test_cases.append(poll_job_result)
    time.sleep(heartbeat_timeout + 2)  # should heartbeat timeout
    poll_job_result = poll_job(start_time, [service, worker], config.PORTFOLIO_VERSION,
                               "Poll for unresponsive worker workflow, 5", ok_expected)
    test_cases.append(poll_job_result)
    time.sleep(heartbeat_timeout + 2)  # should heartbeat timeout and then fail the step function

    test_cases.append(
        get_job(start_time, worker_job_data, "Get Job for unresponsive worker details", ok_expected_job_failed))

    print('Sleeping for 2.0 seconds before canceling jobs')
    time.sleep(2.0)
    test_cases.extend(search_and_cancel_jobs(start_time, "Search and delete all jobs"))
    return test_cases


def main() -> int:
    start_time = time.time()
    test_cases = list()

    test_cases.extend(workflow_for_unresponsive_worker(time.time()))

    test_cases.append(job_manager_health_test(start_time, ok_expected))
    test_cases.append(worker_manager_health_test(time.time(), ok_expected))

    post_job_result = post_job_missing_args(time.time(), bad_request_expected)
    test_cases.append(post_job_result)

    post_job_result = post_job_with_portfolio(time.time(), "First poll for submitted job: post job",
                                              post_job_2xx_expected)
    test_cases.append(post_job_result)
    job_data = json.loads(post_job_result.response.text)
    add_job_defaults(job_data)
    poll_job_result = poll_job(time.time(), config.test_service_and_worker_from_host(config.HOST),
                               config.PORTFOLIO_VERSION, "First poll for submitted job: poll job", ok_expected)
    test_cases.append(poll_job_result)
    worker_job_data = json.loads(poll_job_result.response.text)
    add_worker_defaults(worker_job_data)

    test_cases.append(get_job(time.time(), job_data, "Get Job", ok_expected))
    test_cases.append(heartbeat_job(time.time(), worker_job_data, "Heartbeat a valid job", heartbeat_ok_expected))
    test_cases.append(
        heartbeat_job_new_auth(time.time(), worker_job_data, "Heartbeat a valid job", not_authorized_expected))
    test_cases.append(progress_job_different_auth(time.time(), worker_job_data, expected_progress_details(), 70,
                                                  "Progress API with new auth", not_authorized_expected))
    test_cases.append(heartbeat_job(time.time(), invalid_job_data(), "Heartbeat an invalid job", failure_expected))
    test_cases.append(complete_job_invalid_token(time.time(), job_data, "Complete valid job with invalid job secret"))
    test_cases.append(complete_job_valid_token(time.time(), worker_job_data, "Complete a requested job"))
    test_cases.append(complete_job_already_completed(time.time(), worker_job_data, "Complete the job a second time"))
    test_cases.append(heartbeat_job(time.time(), worker_job_data, "Heartbeat a job after it is completed",
                                    heartbeat_canceled_expected))
    test_cases.append(complete_invalid_job(time.time()))
    test_cases.append(delete_job(time.time(), 'job-does-not-exist', "Delete Invalid Job", failure_expected))
    test_cases.append(delete_job(time.time(), "", "Delete without job id", not_allowed_expected))
    test_cases.extend(get_job_oxygen_token(time.time()))
    test_cases.extend(test_deleting_a_deleted_job(time.time()))
    test_cases.extend(workflow_for_job_with_portfolio_version(time.time()))
    print('Sleeping for 2.0 seconds')
    time.sleep(2.0)
    # TODO: disable this for cosv3 as the workflow is broken when deploying on cosv3
    # Story to investigate https://jira.autodesk.com/browse/CHIMERA-1197
    if not config.IS_COSV3:
        test_cases.extend(workflow_for_killing_job_in_progress(time.time()))
    test_cases.extend(workflow_for_progress(time.time()))
    test_cases.extend(workflow_for_search(time.time()))
    test_cases.extend(workflow_for_anomalous_polling(time.time()))
    test_cases.extend(workflow_for_anomalous_creating(time.time()))
    test_cases.extend(test_creating_job_with_same_idempotencyId(time.time()))
    # Testing impersonation of Worker Manager
    test_cases.extend(workflow_for_impersonation_polling(time.time()))
    # run tests with batch worker
    if not config.DISABLE_BATCH_WORKER_TESTS:
        test_cases.extend(worker_tests.run_batch_tests())

    test_cases.extend(test_add_job_tag(time.time()))
    test_cases.extend(test_delete_job_tag(time.time()))

    # run array job tests
    if not config.DISABLE_BATCH_WORKER_TESTS:
        test_cases.extend(array_job_tests.run_array_job_tests())

    test_suite = TestSuite("CloudOS Compute Test Suite", test_cases)
    xml_doc = test_suite.build_xml_doc()

    with open("./test-results.xml", 'w') as f:
        TestSuite.to_file(f, [test_suite], prettyprint=True)
    print(TestSuite.to_xml_string([test_suite]))
    print("<!-- errors " + xml_doc.attrib.get('errors', '0') + " -->")
    print("<!-- failures " + xml_doc.attrib.get('failures', '0') + " -->")
    return int(xml_doc.attrib.get('errors', '0')) + int(xml_doc.attrib.get('failures', '0'))

if __name__ == "__main__":
    result = main()
    sys.exit(result)

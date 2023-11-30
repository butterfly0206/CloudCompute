#! /usr/bin/env python3
import config


def ok_expected(response):
    return response.status_code == 200


def accepted_expected(response):
    return response.status_code == 202


def not_modified_expected(response):
    return response.status_code == 304

def post_job_2xx_expected(response):
    if config.JOB_CREATE_ASYNCHRONOUS:
        return accepted_expected(response)
    else:
        return ok_expected(response)


def failure_expected(response):
    return response.status_code >= 400


def bad_request_expected(response):
    return response.status_code == 400

def not_authorized_expected(response):
    return response.status_code == 401

def gone_expected(response):
    return response.status_code == 410

def not_allowed_expected(response):
    return response.status_code >= 405

# A successful heartbeat is just a 200 with no body.  Sending 'canceled: false' is a waste
def heartbeat_ok_expected(response):
    return (response.status_code == 200 and
            response.swagger_result == "")

# A heartbeat for a canceled or stopped job should just return 'canceled: true'
def heartbeat_canceled_expected(response):
    return (response.status_code == 200 and
            'canceled' in response.swagger_result and
            response.swagger_result['canceled'] is True)

def search_results_last_one_ok_expected(response):
    jobs = response.swagger_result.jobs
    nextToken = response.swagger_result.nextToken
    return (response.status_code == 200 and
            len(jobs) >= 1 and
            nextToken == '')

def search_results_one_ok_expected(response):
    jobs = response.swagger_result.jobs
    return response.status_code == 200 and len(jobs) == 1

def search_results_some_ok_expected(response):
    jobs = response.swagger_result.jobs
    return response.status_code == 200 and len(jobs) >= 1

def search_results_none_ok_expected(response):
    jobs = response.swagger_result.jobs
    return response.status_code == 200 and len(jobs) == 0

def expected_progress_details():
    return {'progressProperty': 'These are progress details'}

def progress_details_half_ok_expected(response):
    job = response.swagger_result
    return (response.status_code == 200
        and job.progress is not None
        and 'details' in job.progress
        and 'progressProperty' in job.progress.details.keys()
        and job.progress.details['progressProperty'] == expected_progress_details()['progressProperty'])

def expected_results():
    return {'resultsProperty': 'These are job results'}

def expected_details():
    return {'detailsProperty': 'These are job error details'}

def expected_error():
    return '{"code":"500","description":"Worker failure","message":"This is a worker failure error message","details":{}}'

def results_ok_expected(response):
    job = response.swagger_result
    return (response.status_code == 200 and
            'result' in job and
            job.result is not None and
            'keys' in job.result and
            'resultsProperty' in job.result.keys() and
            job.result['resultsProperty'] == expected_results()['resultsProperty'])


def ok_expected_no_results(response):
    job = response.swagger_result
    return (response.status_code == 200 and
            ('result' not in job or
             job.result is None or
             len(job.result) == 0))


def ok_expected_job_failed(response):
    job = response.swagger_result
    return (response.status_code == 200 and
            'status' in job and
            job.status == 'FAILED')


def results_error_expected(response):
    job = response.swagger_result
    return (response.status_code == 200 and
            'errors' in job and
            len(job.errors) == 1 and
            'details' in job.errors[0].keys() and
            'detailsProperty' in job.errors[0]['details'].keys() and
            job.errors[0]['details']['detailsProperty'] == expected_details()['detailsProperty'])


# A successful heartbeat is just a 200 with no body.  Sending 'canceled: false' is a waste
def heartbeat_not_authorized(response):
    return (response.status_code == 401 and
            response.swagger_result == "")

def verify_bearer_token_same(response):
    return ok_expected(response) and response.swagger_result['token'] == config.BEARER_TOKEN

def verify_bearer_token_different(response):
    return ok_expected(response) and response.swagger_result['token'] != config.BEARER_TOKEN

def verify_metadata(response):
    return gone_expected(response)

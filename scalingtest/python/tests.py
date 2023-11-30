# ! /usr/bin/env python3
import asyncio
import os

import bravado.exception
import json
import time
import yaml
from bravado.client import RequestsClient
from bravado.client import SwaggerClient
from junit_xml import TestSuite, TestCase

# Setting LOCAL_DEV is a one stop shop to configure all these rather than burden the config environment
# Start local processes for Job manager and Worker manager and set LOCAL_DEV to one of the following values:
# "sandbox": Target CloudOSV2 sandbox environment
# "dev" <OR any other value> : targets CloudOSV2 dev environment

if os.environ.get('LOCAL_DEV', False):
    HOST = 'localhost'
    JOB_MANAGER_HOST = HOST  # no 'job-manager.' locally
    WORKER_MANAGER_HOST = HOST  # no 'worker-manager.' locally
    JOB_MANAGER_PORT = '8090'
    WORKER_MANAGER_PORT = '8091'
    PROTOCOL = 'http'  # Local server is http to match the process behind the ALB
    if os.environ['LOCAL_DEV'] == 'sandbox':
        LOCAL_SERVICE = 'fpccomp-c-uw2-sb'
    else:
        LOCAL_SERVICE = 'fpccomp-c-uw2'

else:
    HOST = os.environ['APP_URL']
    JOB_MANAGER_PORT = os.environ.get('JOB_MANAGER_PORT', '443')
    WORKER_MANAGER_PORT = os.environ.get('WORKER_MANAGER_PORT', '443')
    JOB_MANAGER_HOST = os.environ.get('JOB_MANAGER_HOST', 'job-manager.' + HOST)
    WORKER_MANAGER_HOST = os.environ.get('WORKER_MANAGER_HOST', 'worker-manager.' + HOST)
    PROTOCOL = os.environ.get('TEST_PROTOCOL', 'https')
    LOCAL_SERVICE = 'fpccomp-c-uw2-sb'


def load_yaml(file_path, base_url):
    script_dir = os.path.dirname(os.path.realpath(__file__))
    with open(os.path.join(script_dir, file_path), 'r') as yaml_file:
        swagger_str = yaml_file.read()
    swagger_dict = yaml.safe_load(swagger_str)
    http_client = RequestsClient()

    # For local work there is no SSL as this is representative of the server behind the ALB
    if os.environ.get('LOCAL_DEV', False):
        http_client.session.verify = False

    return SwaggerClient.from_spec(spec_dict=swagger_dict,
                                   origin_url=base_url + '/swagger.json',
                                   config={'also_return_response': True},
                                   http_client=http_client)

SERVICE_MAP = {
    'fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com': ('fpccomp-c-uw2-sb', 'sample3'),
    'fpccomp-c-uw2.cosv2-c-uw2.autodesk.com': ('fpccomp-c-uw2', 'sample3'),
    'fpccomp-s-ue1-dn.cosv2-s-ue1-dn.autodesk.com': ('fpccomp-s-ue1-dn', 'sample3'),
    'fpccomp-s-ue1-ds.cosv2-s-ue1-ds.autodesk.com': ('fpccomp-s-ue1-ds', 'sample3'),
    'fpccomp-s-ew1-es.cosv2-s-ew1-es.autodesk.com': ('fpccomp-s-ew1-es', 'sample3'),
    'fpccomp-p-ue1-dn.cosv2-p-ue1-dn.autodesk.com': ('fpccomp-p-ue1-dn', 'sample3'),
    'fpccomp-p-ue1-ds.cosv2-p-ue1-ds.autodesk.com': ('fpccomp-p-ue1-ds', 'sample3'),
    'fpccomp-p-ew1-es.cosv2-p-ew1-es.autodesk.com': ('fpccomp-p-ew1-es', 'sample3')
}

def service_and_worker_from_host(host):
    return SERVICE_MAP.get(host.lower(), ('fpccomp-c-uw2-sb', 'sample3'))


def job_manager_url(host):
    return PROTOCOL + '://' + host + ':' + JOB_MANAGER_PORT


def worker_manager_url(host):
    return PROTOCOL + '://' + host + ':' + WORKER_MANAGER_PORT


WM_CLIENT = load_yaml('./worker-manager.yaml', worker_manager_url(WORKER_MANAGER_HOST))
JM_CLIENT = load_yaml('./job-manager.yaml', job_manager_url(JOB_MANAGER_HOST))


def ok_expected(response):
    return response.status_code == 200


def failure_expected(response):
    return response.status_code >= 400


def error_expected(response):
    return response.status_code == 500


def handle_response(response, test_case_name, start_time, acceptance_test):
    test_case = TestCase(name=test_case_name, elapsed_sec=time.time() - start_time)
    test_case.response = response
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
        test_case.stdout = 'HTTP {0:d}: {1}'.format(response.status_code, response.text)
    return test_case


def execute_http(lambda_to_call, label, start_time, acceptance_test):
    try:
        result = lambda_to_call()
        return handle_response(result[1], label, start_time, acceptance_test)
    except bravado.exception.HTTPError as e:
        response = getattr(e, 'response', None)
        return handle_response(response, label, start_time, acceptance_test)



TESTS = {
    "testBatchScaling": {"testDefinition": {"duration": 50, "progressInterval": 10, "progressUntil": 100,
                                        "heartbeatInterval": 10, "heartbeatUntil": 52, "cpu" : 2, "memory" : 100,
                                        "output": {"status": "COMPLETED"}}}
    }

TEST_TIMEOUT = 5000

def post_job(testType,  start_time, acceptance_test):
    JobArgs = JM_CLIENT.get_model('JobArgs')
    service, worker = service_and_worker_from_host(HOST)
    job = JobArgs(
        tags=['test', 'completion'],
        service=service,
        worker=worker,
        payload=TESTS[testType]
    )
    return execute_http(lambda: JM_CLIENT.developers.createJob(jobArgs=job).result(), "Post Job", start_time, acceptance_test)


async def check_job_status(testType, job_data, test_case):
    test_case.status = "FAILED"
    while True:
        await asyncio.sleep(10.0)
        result = JM_CLIENT.developers.getJob(id=job_data['jobID']).result()[0]
        if result["status"] == TESTS[testType]["testDefinition"]["output"]["status"]:
            test_case.status = "SUCCESS"
            break
        elif result["status"] != "CREATED" and result["status"] != "SCHEDULED" and result["status"] != "INPROGRESS":
            test_case.add_failure_info('Job({}: expecting status of {} and received {}'
                                       .format(job_data['jobID'], TESTS[testType]["testDefinition"]["output"]["status"], result["status"]))
            break


async def run_test(testType, start_time):
    test_case = TestCase(name=testType)

    post_job_result = post_job(testType, start_time, ok_expected)
    #await asyncio.sleep(1.0)
    job_data = json.loads(post_job_result.response.text)
    print(job_data)
    if job_data.get('jobID') is not None :
        test_case.log = str(job_data.get('jobID'))
    try:
        await asyncio.wait_for(check_job_status(testType, job_data, test_case), TEST_TIMEOUT)
    except asyncio.TimeoutError:
        test_case.status = "FAILED"
        test_case.add_failure_info('Test "{}" did not finish in {} seconds'.format(testType, TEST_TIMEOUT))
    test_case.elapsed_sec = time.time() - start_time
    return test_case


def run_batch_tests():
    count = int(os.environ.get('NUM_JOBS', 2))
    test_funcs = []
    for _ in range(0, count):
        test_funcs.append(run_test("testBatchScaling", time.time()))

    event_loop = asyncio.get_event_loop()
    event_loop.set_debug(enabled=True)
    test_cases = event_loop.run_until_complete(asyncio.gather(*test_funcs))
    event_loop.close()

    return test_cases


def main() -> None:
    test_cases = run_batch_tests()
    test_suite = TestSuite("CloudOS Compute Scaling Test Suite", test_cases)

    with open("./test-results.xml", 'w') as f:
        TestSuite.to_file(f, [test_suite], prettyprint=True)
    print(TestSuite.to_xml_string([test_suite]))
    return


if __name__ == "__main__":
    main()

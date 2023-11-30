#! /usr/bin/env python3
import base64
import os

import boto3
import jmclient
import jmclient.api.developers_api
import jmclient.api_client
import jmclient.configuration
import requests

import read_cosv3_test_secrets
import wmclient
import wmclient.api.default_api
import wmclient.api_client
import yaml
from bravado.client import RequestsClient
from bravado.client import SwaggerClient
from six.moves.urllib import parse as urlparse

# Setting LOCAL_DEV is a one stop shop to configure all these rather than burden the config environment
# Choose the value 'sandbox' for sandbox, anything else for dev
if os.environ.get('LOCAL_DEV', False):
    HOST = 'localhost'
    JOB_MANAGER_HOST = HOST  # no 'job-manager.' locally
    WORKER_MANAGER_HOST = HOST  # no 'worker-manager.' locally
    JOB_MANAGER_PORT = '8090'
    WORKER_MANAGER_PORT = '8091'
    IS_SANDBOX = os.environ['LOCAL_DEV'] == 'sandbox'
    PROTOCOL = 'http'  # Local server is http to match the process behind the ALB
    LOCAL_SERVICE = 'fpccomp-c-uw2-sb' if IS_SANDBOX else 'fpccomp-c-uw2'
    USE_APIGEE = False
    FORCE_BEARER_TOKEN = IS_SANDBOX
    OXYGEN_CLIENT_ID = ''
    OXYGEN_CLIENT_SECRET = ''
    APP_MONIKER = 'fpccomp-c-uw2-sb' if IS_SANDBOX else 'fpccomp-c-uw2'
    TEST_MONIKER = 'fpcexapp-c-uw2-sb' if IS_SANDBOX else 'fpcexapp-c-uw2'
    PORTFOLIO_VERSION = os.environ.get('COS_PORTFOLIO_VERSION', 'deployed')
    COS_MONIKER = 'cosv2-c-uw2-sb' if IS_SANDBOX else 'cosv2-c-uw2'
    APIGEE_HOST = 'developer-dev.api.autodesk.com' if FORCE_BEARER_TOKEN else ''
else:
    HOST = os.environ['APP_URL']
    JOB_MANAGER_PORT = os.environ.get('JOB_MANAGER_PORT', '443')
    WORKER_MANAGER_PORT = os.environ.get('WORKER_MANAGER_PORT', '443')
    JOB_MANAGER_HOST = os.environ.get('JOB_MANAGER_HOST', 'job-manager.' + HOST)
    WORKER_MANAGER_HOST = os.environ.get('WORKER_MANAGER_HOST', 'worker-manager.' + HOST)
    PROTOCOL = os.environ.get('TEST_PROTOCOL', 'https')
    IS_SANDBOX = False
    LOCAL_SERVICE = 'fpccomp-c-uw2-sb'
    USE_APIGEE = os.environ.get('APIGEE_REQUIRED', 'False').casefold() == 'True'.casefold()
    FORCE_BEARER_TOKEN = os.environ.get('FORCE_BEARER_TOKEN', 'False').casefold() == 'True'.casefold()
    APIGEE_HOST = os.environ.get('APIGEE_HOST', 'developer-dev.api.autodesk.com')
    APP_MONIKER = os.environ.get('APP_MONIKER', 'fpccomp-c-uw2-sb')
    TEST_MONIKER = os.environ.get('TEST_MONIKER', 'fpcexapp-c-uw2-sb')
    PORTFOLIO_VERSION = os.environ.get('COS_PORTFOLIO_VERSION', 'deployed')
    COS_MONIKER = os.environ.get('COS_MONIKER', 'cosv2-c-uw2-sb')

WORKER = os.environ.get('WORKER', 'sample1')
IS_COSV3 = HOST.endswith('.cloudos.autodesk.com')
JOB_CREATE_ASYNCHRONOUS = os.environ.get('JOB_CREATE_ASYNCHRONOUS', 'False').casefold() == 'True'.casefold()
DISABLE_BATCH_WORKER_TESTS = os.environ.get('DISABLE_BATCH_WORKER_TESTS', 'False').casefold() == 'True'.casefold()
IS_FEDRAMP = '-sfr-' in APP_MONIKER.lower() or '-pfr-' in APP_MONIKER.lower()

def apigee_prefix(app_url):
    return APIGEE_MAP.get(app_url, APIGEE_MAP.get('localhost'))


def modify_for_apigee(yaml, use_apigee):
    if use_apigee:
        yaml['basePath'] = '/' + apigee_prefix(HOST) + '/jm' + yaml['basePath']
    return yaml


def modify_for_invalid_job_tags(yaml):
    yaml['definitions']['JobArgs']['allOf'][1]['properties']['tags'] = {"type": "string"};
    return yaml


def load_yaml(file_path):
    script_dir = os.path.dirname(os.path.realpath(__file__))
    with open(os.path.join(script_dir, file_path), 'r') as yaml_file:
        swagger_str = yaml_file.read()
    return yaml.safe_load(swagger_str)


def get_test_secrets(moniker):
    if IS_COSV3:
        return read_cosv3_test_secrets.read_test_secrets()

    ssm = boto3.client('ssm')
    parameter_list = []
    paginator = ssm.get_paginator('get_parameters_by_path')
    secret_path = '/{}/test/'.format(moniker)
    secret_iterator = paginator.paginate(Path=secret_path, Recursive=True, WithDecryption=True)
    for one_page in secret_iterator:
        parameter_list += one_page['Parameters']
    parameter_map = { parameter['Name'].replace(secret_path, ''): parameter['Value'] for parameter in parameter_list }
    return parameter_map


def get_bearer_token(client_id, client_secret, scope='data:read'):
    # Switching to v2 of auth api as Apigee-X does not seem to support v1
    # https://wiki.autodesk.com/display/FIAMPA/Migrate+from+OAuth+2+V1+to+V2+endpoints+for+2L+tokens
    basic_auth = base64.b64encode(f'{client_id}:{client_secret}'.encode('ascii')).decode('ascii')
    payload = {
        'grant_type': 'client_credentials',
        'scope': scope
    }
    response = requests.post(
        get_apigee_auth_url(),
        headers={
            'Content-Type': 'application/x-www-form-urlencoded',
            'Authorization': f"Basic {basic_auth}",
        },
        data=payload)
    if response.status_code != 200:
        raise RuntimeError(
            'Getting bearer token returned {:d} with body {:s}'.format(
                response.status_code, response.text))
    loaded_json = response.json()
    return loaded_json['access_token']

def get_cloudos_url():
    return 'https://{}.autodesk.com'.format(COS_MONIKER)

def get_apigee_auth_url():
    return 'https://{}/authentication/v2/token'.format(APIGEE_HOST)

# This code comes from requests_client.py in the bravado library.
# Both the host and port need to be matched. So https://worker-manager.fpccomp-c-uw2.cosv2-c-uw2.autodesk.com:443
# becomes worker-manager.fpccomp-c-uw2.cosv2-c-uw2.autodesk.com:443, and that's why the match is required.
def auth_matcher_string(base_url):
    split = urlparse.urlsplit(base_url)
    return split.netloc

def swagger_to_client(swagger_dict, base_url, auth_type, auth_token):
    http_client = RequestsClient()

    if auth_token:
        # Use exactly the logic that matches in bravado
        auth_matcher = auth_matcher_string(base_url)
        http_client.set_api_key(host=auth_matcher, api_key=auth_type + ' ' + auth_token, param_name='Authorization',
                                param_in='header')


    # For local work there is no SSL as this is representative of the server behind the ALB
    if os.environ.get('LOCAL_DEV', False):
        http_client.session.verify = False

    return SwaggerClient.from_spec(spec_dict=swagger_dict,
        origin_url=base_url+'/swagger.json',
        config={'also_return_response': True},
        http_client=http_client)

def openapi_jm_client(base_url, auth_token):
    openapi_config = jmclient.configuration.Configuration(host=base_url + "/api/v1")
    openapi_config.access_token = auth_token
    return jmclient.api.developers_api.DevelopersApi(jmclient.api_client.ApiClient(openapi_config))

def openapi_wm_client(base_url, username, password):
    openapi_config = wmclient.configuration.Configuration(host=base_url + "/api/v1", username=username, password=password)
    return wmclient.api.default_api.DefaultApi(wmclient.api_client.ApiClient(openapi_config))

SERVICE_MAP = {
    'fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com': ('fpccomp-c-uw2-sb', WORKER),
    'fpccomp-c-uw2.cosv2-c-uw2.autodesk.com': ('fpccomp-c-uw2', WORKER),
    'fpccomp-s-ue1-dn.cosv2-s-ue1-dn.autodesk.com': ('fpccomp-s-ue1-dn', WORKER),
    'fpccomp-s-ue1-ds.cosv2-s-ue1-ds.autodesk.com': ('fpccomp-s-ue1-ds', WORKER),
    'fpccomp-s-ew1-es.cosv2-s-ew1-es.autodesk.com': ('fpccomp-s-ew1-es', WORKER),
    'fpccomp-p-ue1-dn.cosv2-p-ue1-dn.autodesk.com': ('fpccomp-p-ue1-dn', WORKER),
    'fpccomp-p-ue1-ds.cosv2-p-ue1-ds.autodesk.com': ('fpccomp-p-ue1-ds', WORKER),
    'fpccomp-p-ew1-es.cosv2-p-ew1-es.autodesk.com': ('fpccomp-p-ew1-es', WORKER),
    'fpccomp-c-uw2-sb-test.cosv2-c-uw2-sb.autodesk.com': ('fpccomp-c-uw2-sb', WORKER),
    'fpccomp-c-uw2-test.cosv2-c-uw2.autodesk.com': ('fpccomp-c-uw2', WORKER),
    'fpccomp-s-ue1-dn-test.cosv2-s-ue1-dn.autodesk.com': ('fpccomp-s-ue1-dn', WORKER),
    'fpccomp-s-ue1-ds-test.cosv2-s-ue1-ds.autodesk.com': ('fpccomp-s-ue1-ds', WORKER),
    'fpccomp-s-ew1-es-test.cosv2-s-ew1-es.autodesk.com': ('fpccomp-s-ew1-es', WORKER),
    'fpccomp-p-ue1-dn-test.cosv2-p-ue1-dn.autodesk.com': ('fpccomp-p-ue1-dn', WORKER),
    'fpccomp-p-ue1-ds-test.cosv2-p-ue1-ds.autodesk.com': ('fpccomp-p-ue1-ds', WORKER),
    'fpccomp-p-ew1-es-test.cosv2-p-ew1-es.autodesk.com': ('fpccomp-p-ew1-es', WORKER),
    'localhost': (LOCAL_SERVICE, WORKER),
    'fpccomp-c-uw2.cloudos.autodesk.com': ('fpccomp-c-uw2', WORKER),
    'fpccomp-c-uw2-test.cloudos.autodesk.com': ('fpccomp-c-uw2', WORKER),
    'fpccomp-s-ue1-test.cloudos.autodesk.com': ('fpccomp-s-ue1', WORKER),
    'fpccomp-s-ue1.cloudos.autodesk.com': ('fpccomp-s-ue1', WORKER),
    'fpccomp-s-ew1-test.cloudos.autodesk.com': ('fpccomp-s-ew1', WORKER),
    'fpccomp-s-ew1.cloudos.autodesk.com': ('fpccomp-s-ew1', WORKER),
    'fpccomp-sfr-ue2-test.cloudos.autodesk.com': ('fpccomp-sfr-ue2', WORKER),
    'fpccomp-sfr-ue2.cloudos.autodesk.com': ('fpccomp-sfr-ue2', WORKER),
    'fpccomp-p-ue1-test.cloudos.autodesk.com': ('fpccomp-p-ue1', WORKER),
    'fpccomp-p-ue1.cloudos.autodesk.com': ('fpccomp-p-ue1', WORKER),
    'fpccomp-p-ew1-test.cloudos.autodesk.com': ('fpccomp-p-ew1', WORKER),
    'fpccomp-p-ew1.cloudos.autodesk.com': ('fpccomp-p-ew1', WORKER),
    'fpccomp-p-as2.cloudos.autodesk.com': ('fpccomp-p-as2', WORKER),
    'fpccomp-pfr-ue2.cloudos.autodesk.com': ('fpccomp-pfr-ue2', WORKER),
    'fpccomp-pfr-ue2-test.cloudos.autodesk.com': ('fpccomp-pfr-ue2', WORKER)
}

APIGEE_MAP = {
    'fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com':         'fpccomp-c-uw2-sb',
    'fpccomp-c-uw2.cosv2-c-uw2.autodesk.com':               'fpccomp-c-uw2',
    'fpccomp-s-ue1-dn.cosv2-s-ue1-dn.autodesk.com':         'fpccomp-s-ue1-dn',
    'fpccomp-s-ue1-ds.cosv2-s-ue1-ds.autodesk.com':         'fpccomp-s-ue1-ds',
    'fpccomp-s-ew1-es.cosv2-s-ew1-es.autodesk.com':         'fpccomp-s-ew1-es',
    'fpccomp-p-ue1-dn.cosv2-p-ue1-dn.autodesk.com':         'fpccomp-p-ue1-dn',
    'fpccomp-p-ue1-ds.cosv2-p-ue1-ds.autodesk.com':         'fpccomp-p-ue1-ds',
    'fpccomp-p-ew1-es.cosv2-p-ew1-es.autodesk.com':         'fpccomp-p-ew1-es',
    'fpccomp-c-uw2-sb-test.cosv2-c-uw2-sb.autodesk.com':    'fpccomp-c-uw2-sb-test',
    'fpccomp-c-uw2-test.cosv2-c-uw2.autodesk.com':          'fpccomp-c-uw2-test',
    'fpccomp-s-ue1-dn-test.cosv2-s-ue1-dn.autodesk.com':    'fpccomp-s-ue1-dn-test',
    'fpccomp-s-ue1-ds-test.cosv2-s-ue1-ds.autodesk.com':    'fpccomp-s-ue1-ds-test',
    'fpccomp-s-ew1-es-test.cosv2-s-ew1-es.autodesk.com':    'fpccomp-s-ew1-es-test',
    'fpccomp-p-ue1-dn-test.cosv2-p-ue1-dn.autodesk.com':    'fpccomp-p-ue1-dn-test',
    'fpccomp-p-ue1-ds-test.cosv2-p-ue1-ds.autodesk.com':    'fpccomp-p-ue1-ds-test',
    'fpccomp-p-ew1-es-test.cosv2-p-ew1-es.autodesk.com':    'fpccomp-p-ew1-es-test',
    'localhost': 'fpccomp-c-uw2-sb',
    'fpccomp-c-uw2.cloudos.autodesk.com':                   'fpccomp',
    'fpccomp-s-ue1.cloudos.autodesk.com':                   'fpccomp',
    'fpccomp-s-ew1.cloudos.autodesk.com':                   'fpccomp-ew1',
    'fpccomp-sfr-ue2.cloudos.autodesk.com':                 'fpccomp',
    'fpccomp-p-ue1.cloudos.autodesk.com':                   'fpccomp',
    'fpccomp-p-ew1.cloudos.autodesk.com':                   'fpccomp-ew1',
    'fpccomp-p-as2.cloudos.autodesk.com':                   'fpccomp-as2',
    'fpccomp-pfr-ue2.cloudos.autodesk.com':                 'fpccomp',
}

TEST_SECRETS = get_test_secrets(APP_MONIKER)

BEARER_TOKEN = get_bearer_token(TEST_SECRETS['OXYGEN_CLIENT_ID'],
                                TEST_SECRETS['OXYGEN_CLIENT_SECRET']) if (USE_APIGEE or FORCE_BEARER_TOKEN) else ''
COS_WORKER_SECRET = TEST_SECRETS.get('COS_WORKER_SECRET', '')

WM_YAML = load_yaml('./worker-manager.yaml')
JM_YAML = modify_for_apigee(load_yaml('./job-manager.yaml'), USE_APIGEE)
JM_YAML_INVALID_TAGS = modify_for_invalid_job_tags(modify_for_apigee(load_yaml('./job-manager.yaml'), USE_APIGEE))

def job_manager_url(host, use_apigee):
    if use_apigee:
        return 'https://{:s}/{:s}/jm'.format(APIGEE_HOST, apigee_prefix(HOST))
    else:
        return '{}://{}:{}'.format(PROTOCOL, host, JOB_MANAGER_PORT)

def worker_manager_url(host):
        return '{}://{}:{}'.format(PROTOCOL, host, WORKER_MANAGER_PORT)

def worker_credential(moniker, secret):
    return base64.b64encode(bytes('{}:{}'.format(moniker, secret), 'utf-8')).decode('ascii')

#Gets the secrets associated with the TEST_MONIKER that have also been added to the testSecrets for APP_MONIKER
#and stored as COS_ALT_WORKER_SECRET. This is because we don't want to add dependency on fpcexapp from fpccomp
#for integration tests.
def get_secrets_of_test_moniker():
    COS_WORKER_SECRET_TEST_MONIKER = TEST_SECRETS.get('COS_ALT_WORKER_SECRET', '')
    return COS_WORKER_SECRET_TEST_MONIKER

COS_WORKER_SECRET_TEST_MONIKER = get_secrets_of_test_moniker()

WM_CLIENT = swagger_to_client(WM_YAML, worker_manager_url(WORKER_MANAGER_HOST), 'Basic', worker_credential(APP_MONIKER, COS_WORKER_SECRET))
JM_CLIENT = swagger_to_client(JM_YAML, job_manager_url(JOB_MANAGER_HOST, USE_APIGEE), 'Bearer', BEARER_TOKEN)
JM_CLIENT_INVALID_TAGS = swagger_to_client(JM_YAML_INVALID_TAGS, job_manager_url(JOB_MANAGER_HOST, USE_APIGEE),
                                           'Bearer',
                                           BEARER_TOKEN)

OPENAPI_JM_CLIENT = openapi_jm_client(job_manager_url(JOB_MANAGER_HOST, USE_APIGEE), BEARER_TOKEN)

OPENAPI_WM_CLIENT = openapi_wm_client(worker_manager_url(WORKER_MANAGER_HOST), APP_MONIKER, COS_WORKER_SECRET)

WM_NEW_AUTH = swagger_to_client(WM_YAML, worker_manager_url(WORKER_MANAGER_HOST), 'Basic', worker_credential(TEST_MONIKER,
                                                                                             COS_WORKER_SECRET_TEST_MONIKER))

# This high timeout is for batch since we may have 0 instance running for Batch and there could be delay of around
# 10 minutes before an instance is up and a batch job get to run.
BATCH_TEST_TIMEOUT = 1200

BATCH_WORKERS = ['sample2', 'sisample']

LOCAL_BATCH_WORKERS = ['sample2']

BATCH_SERVICE_MAP = {

    'fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com': 'fpccomp-c-uw2-sb',
    'fpccomp-c-uw2-sb-test.cosv2-c-uw2-sb.autodesk.com': 'fpccomp-c-uw2-sb',

    'fpccomp-c-uw2.cosv2-c-uw2.autodesk.com': 'fpccomp-c-uw2',
    'fpccomp-c-uw2-test.cosv2-c-uw2.autodesk.com': 'fpccomp-c-uw2',

    'fpccomp-s-ue1-dn.cosv2-s-ue1-dn.autodesk.com': 'fpccomp-s-ue1-dn',
    'fpccomp-s-ue1-dn-test.cosv2-s-ue1-dn.autodesk.com': 'fpccomp-s-ue1-dn',

    'fpccomp-s-ue1-ds.cosv2-s-ue1-ds.autodesk.com': 'fpccomp-s-ue1-ds',
    'fpccomp-s-ue1-ds-test.cosv2-s-ue1-ds.autodesk.com': 'fpccomp-s-ue1-ds',

    'fpccomp-s-ew1-es.cosv2-s-ew1-es.autodesk.com': 'fpccomp-s-ew1-es',
    'fpccomp-s-ew1-es-test.cosv2-s-ew1-es.autodesk.com': 'fpccomp-s-ew1-es',

    'fpccomp-p-ue1-dn.cosv2-p-ue1-dn.autodesk.com': 'fpccomp-p-ue1-dn',
    'fpccomp-p-ue1-dn-test.cosv2-p-ue1-dn.autodesk.com': 'fpccomp-p-ue1-dn',

    'fpccomp-p-ue1-ds.cosv2-p-ue1-ds.autodesk.com': 'fpccomp-p-ue1-ds',
    'fpccomp-p-ue1-ds-test.cosv2-p-ue1-ds.autodesk.com': 'fpccomp-p-ue1-ds',

    'fpccomp-p-ew1-es.cosv2-p-ew1-es.autodesk.com': 'fpccomp-p-ew1-es',
    'fpccomp-p-ew1-es-test.cosv2-p-ew1-es.autodesk.com': 'fpccomp-p-ew1-es',

    # COSV3
    'fpccomp-c-uw2.cloudos.autodesk.com': 'fpccomp-c-uw2',
    'fpccomp-c-uw2-test.cloudos.autodesk.com': 'fpccomp-c-uw2',

    'fpccomp-s-ue1.cloudos.autodesk.com': 'fpccomp-s-ue1',
    'fpccomp-s-ue1-test.cloudos.autodesk.com': 'fpccomp-s-ue1',

    'fpccomp-s-ew1.cloudos.autodesk.com': 'fpccomp-s-ew1',
    'fpccomp-s-ew1-test.cloudos.autodesk.com': 'fpccomp-s-ew1',

    'fpccomp-sfr-ue2.cloudos.autodesk.com': 'fpccomp-sfr-ue2',
    'fpccomp-sfr-ue2-test.cloudos.autodesk.com': 'fpccomp-sfr-ue2',

    'fpccomp-p-ue1.cloudos.autodesk.com': 'fpccomp-p-ue1',
    'fpccomp-p-ue1-test.cloudos.autodesk.com': 'fpccomp-p-ue1',

    'fpccomp-p-ew1.cloudos.autodesk.com': 'fpccomp-p-ew1',
    'fpccomp-p-ew1-test.cloudos.autodesk.com': 'fpccomp-p-ew1',

    'fpccomp-p-as2.cloudos.autodesk.com': 'fpccomp-p-as2',

    'fpccomp-pfr-ue2.cloudos.autodesk.com': 'fpccomp-pfr-ue2',
    'fpccomp-pfr-ue2-test.cloudos.autodesk.com': 'fpccomp-pfr-ue2',

    'localhost': LOCAL_SERVICE
}


def batch_service_and_workers_from_host(host):
    service = BATCH_SERVICE_MAP.get(host.lower(), 'fpccomp-c-uw2-sb')
    if host.lower() == 'localhost':
        workers = LOCAL_BATCH_WORKERS
    else:
        workers = BATCH_WORKERS
    return service, workers


def test_service_and_worker_from_host(host):
    return SERVICE_MAP.get(host.lower(), SERVICE_MAP.get('fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com'))


def get_auth_details(test_moniker):
    COS_WORKER_SECRET_TEST_MONIKER = get_secrets_of_test_moniker()
    auth_type = 'Basic'
    auth_token = worker_credential(test_moniker, COS_WORKER_SECRET_TEST_MONIKER)
    return header_options(auth_type, auth_token)


def header_options(auth_type, auth_token):
    headers = {
        'Authorization': auth_type + ' ' + auth_token
    }
    return headers

#Get the authentication header for APP_MONIKER that will be passed as headers to WM API calls.
def get_auth_details_app_moniker(app_moniker):
    auth_token = worker_credential(app_moniker, COS_WORKER_SECRET)
    auth_type = 'Basic'
    return header_options(auth_type, auth_token)

#Parameter for getting authentication header for APP_MONIKER for Worker Manager API calls.
WM_AUTH_HEADER = get_auth_details_app_moniker(APP_MONIKER)

#Parameter for getting authentication for test Moniker which will be used for testing impersonation of WM
WM_TEST_AUTH_HEADER = get_auth_details(TEST_MONIKER)

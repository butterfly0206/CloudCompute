# /usr/bin/python3

"""
filename: make_worker_credentials.py
Author: Dave Watt <dave.watt@autodesk.com>
Description: Create or update the credentials for CloudOS Compute workers in a cluster.
"""

import argparse
import asyncio
import boto3
import hvac
import logging
import os
import requests
import string
import sys
import yaml
from bravado.client import RequestsClient
from bravado.client import SwaggerClient
from random import *

# Global async event loop
EVENT_LOOP = None

SSM = boto3.client('ssm')

# For use getting the swagger client built
API_VERSION = '/api/v1'
# This is for getting the secrets
APP_MONIKER = os.environ.get('APP_MONIKER')
# For calling through apigee to submit jobs - default to dev
APIGEE_HOST = os.environ.get('APIGEE_HOST')

# Labels for the two versions of secrets - Current and Fallback.
CURRENT = 'Current'
FALLBACK = 'Fallback'

# Protocol for calling the job manager
PROTOCOL = os.environ.get('TEST_PROTOCOL', 'https')

# HTTP port for the job manager. Might be 8081 locally, but 443 everywhere else
JOB_MANAGER_PORT = os.environ.get('JOB_MANAGER_PORT', '443')

# The Compute host path for where the job manager is located
HOST = os.environ.get('APP_URL', 'fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com')

# The job manager host name
JOB_MANAGER_HOST = os.environ.get(
    'JOB_MANAGER_HOST', 'job-manager.' + HOST if HOST != 'localhost' else HOST)

# Go through Apigee to reach the job manager. Defaults to True; False is allowed
# in dev, but True also works there.
USE_APIGEE = os.environ.get('APIGEE_REQUIRED', 'True').casefold() == 'True'.casefold()

# CloudOS monikers from Compute monikers; used for secret-reading
COS_MONIKER_MAP = {
    'fpccomp-c-uw2-sb': 'cosv2-c-uw2-sb',
    'fpccomp-c-uw2': 'cosv2-c-uw2',
    'fpccomp-s-ue1-dn': 'cosv2-s-ue1-dn',
    'fpccomp-s-ue1-ds': 'cosv2-s-ue1-ds',
    'fpccomp-s-ew1-es': 'cosv2-s-ew1-es',
    'fpccomp-p-ue1-dn': 'cosv2-p-ue1-dn',
    'fpccomp-p-ue1-ds': 'cosv2-p-ue1-ds',
    'fpccomp-p-ew1-es': 'cosv2-p-ew1-es'
}

# Prefixes in apigee for the various Compute deployments for reaching the job manager
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
    
    'localhost': 'fpccomp-c-uw2-sb'
}

VAULT_ADDR = os.environ.get('VAULT_ADDR', 'https://civ1.dv.adskengineer.net:8200')

def read_vault_token():
    """Read the vault token from ~/.vault-token"""
    home_folder = os.path.expanduser("~")
    with open(os.path.join(home_folder, '.vault-token'), encoding='utf8') as f:
        return f.read().strip()

VAULT_TOKEN = read_vault_token()

VAULT_CLIENT = hvac.Client(url=VAULT_ADDR)

def is_vault_token_valid(token):
    return VAULT_CLIENT.is_authenticated()

def get_moniker_app_secrets(moniker):
    cloudos_moniker = COS_MONIKER_MAP[APP_MONIKER]
    read_response = VAULT_CLIENT.read(f"{cloudos_moniker}/{moniker}/generic/appSecrets")
    if not read_response:
        return {}
    return read_response['data']

def write_moniker_app_secrets(moniker, secrets):
    cloudos_moniker = COS_MONIKER_MAP[APP_MONIKER]
    return VAULT_CLIENT.write(f"{cloudos_moniker}/{moniker}/generic/appSecrets", **secrets)

def get_moniker_test_secrets(moniker):
    cloudos_moniker = COS_MONIKER_MAP[APP_MONIKER]
    read_response = VAULT_CLIENT.read(f"{cloudos_moniker}/{moniker}/generic/testSecrets")
    if not read_response:
        return {}
    return read_response['data']

def write_moniker_test_secrets(moniker, secrets):
    cloudos_moniker = COS_MONIKER_MAP[APP_MONIKER]
    return VAULT_CLIENT.write(f"{cloudos_moniker}/{moniker}/generic/testSecrets", **secrets)

def apigee_prefix(app_url):
    return APIGEE_MAP.get(app_url, APIGEE_MAP.get('localhost'))

def get_apigee_auth_url():
    return 'https://{}/authentication/v1/authenticate'.format(APIGEE_HOST)

def job_manager_url(host, use_apigee):
    if use_apigee:
        return 'https://{:s}/{:s}/jm'.format(APIGEE_HOST, apigee_prefix(HOST))
    else:
        return '{}://{}:{}'.format(PROTOCOL, host, JOB_MANAGER_PORT)

def get_secrets(secret_path):
    ssm = boto3.client('ssm')
    parameter_list = []
    paginator = ssm.get_paginator('get_parameters_by_path')
    secret_iterator = paginator.paginate(Path=secret_path, Recursive=True, WithDecryption=True)
    for one_page in secret_iterator:
        parameter_list += one_page['Parameters']
    parameter_map = { parameter['Name'].replace(secret_path, ''): parameter['Value'] for parameter in parameter_list }
    return parameter_map

def get_cloudos_secrets():
    cloudos_moniker = COS_MONIKER_MAP[APP_MONIKER]
    return get_secrets(f"/{cloudos_moniker}/secrets/")

def get_test_secrets():
    return get_secrets(f"/{APP_MONIKER}/test/")

def get_existing_credentials():
    return get_secrets("/ComputeWorkerCredentials/")

def swagger_to_client(swagger_dict, base_url, bearer_token):
    http_client = RequestsClient()
    if bearer_token:
        http_client.set_api_key(host=APIGEE_HOST, api_key='Bearer ' + bearer_token, param_name='Authorization', param_in='header')

    return SwaggerClient.from_spec(spec_dict=swagger_dict,
        origin_url=base_url+'/swagger.json',
        config={ 'also_return_response': True },
        http_client=http_client)

def get_bearer_token(client_id, client_secret, scope='data:read'):
    payload = {
        'client_id': client_id,
        'client_secret': client_secret,
        'grant_type': 'client_credentials',
        'scope': scope
    }
    response = requests.post(
        get_apigee_auth_url(),
        headers={'Content-Type': 'application/x-www-form-urlencoded'},
        data=payload)
    if response.status_code != 200:
        raise RuntimeError(
            'Getting bearer token returned {:d} with body {:s}'.format(
                response.status_code, response.text))
    loaded_json = response.json()
    return loaded_json['access_token']

def modify_for_apigee(yaml, use_apigee):
    if use_apigee:
        yaml['basePath'] = '/' + apigee_prefix(HOST) + '/jm' + yaml['basePath']
    return yaml

def load_yaml(file_path):
    script_dir = os.path.dirname(os.path.realpath(__file__))
    with open(os.path.join(script_dir, file_path), 'r') as yaml_file:
        swagger_str = yaml_file.read()
    return yaml.safe_load(swagger_str)

TEST_SECRETS = get_test_secrets()

CLOUDOS_SECRETS = get_cloudos_secrets()

BEARER_TOKEN = get_bearer_token(CLOUDOS_SECRETS['OAuthClientID'], CLOUDOS_SECRETS['OAuthClientSecret']) if USE_APIGEE else ''

JM_YAML = modify_for_apigee(load_yaml('./job-manager.yaml'), USE_APIGEE)

JM_CLIENT = swagger_to_client(JM_YAML, job_manager_url(JOB_MANAGER_HOST, USE_APIGEE), BEARER_TOKEN)
    
def generate_random_password():
    def pick(num_chars, character_set):
        return [c for c in map(lambda n: choice(character_set), range(0, num_chars))]
    min_numbers = 4
    min_special_characters = 4
    min_uppercase_letters = 3
    min_lowercase_letters = 3

    password = pick(min_numbers, string.digits)
    password.extend(pick(min_special_characters, string.punctuation))
    password.extend(pick(min_uppercase_letters, string.ascii_uppercase))
    password.extend(pick(min_lowercase_letters, string.ascii_lowercase))

    shuffle(password)
    return "".join(password)

def make_portfolio_key(job_definition):
    return f"{job_definition['service']}"

def gather_workers(workers):
    result = {}
    for one_job_definition in workers:
        key = make_portfolio_key(one_job_definition)
        if key in result:
            result[key]['workers'].add(one_job_definition.worker)
        else:
            result[make_portfolio_key(one_job_definition)] = {
                'workers': {one_job_definition.worker}
            }
    return result

def make_gathered_worker(moniker):
    result = {}
    result[moniker] = {
        'workers': {'sample'}
    }
    return result

def assemble_passwords(gathered_workers):
    for moniker, workers in gathered_workers.items():
        workers['password'] =  generate_random_password()
        gathered_workers[moniker] = workers
    return gathered_workers

def write_labels(secret_path, version, labels):
    if len(labels) == 0:
        return
    SSM.label_parameter_version(
        Name=secret_path,
        ParameterVersion=version,
        Labels=labels
    )

def write_secret(secret_path, secret_value, key):
    return SSM.put_parameter(
        Name=secret_path,
        Type='SecureString',
        KeyId=key,
        Overwrite=True,
        Value=secret_value
    )

def write_secrets_and_labels(workers_and_passwords):
    encryption_key = f"alias/{APP_MONIKER}"
    for one_moniker, one_entry in workers_and_passwords.items():
        secret_path = f"/ComputeWorkerCredentials/{one_moniker}"
        secret_result = write_secret(
            secret_path, one_entry['password'], encryption_key
        )
        # Fallback needs to be written before current, to avoid a possibility that
        # the current workers might temporarily not have a valid password
        if (secret_result['Version'] > 1):
            write_labels(secret_path, secret_result['Version'] - 1, [FALLBACK])
            write_labels(secret_path, secret_result['Version'], [CURRENT])
        else:
            write_labels(secret_path, secret_result['Version'], [CURRENT, FALLBACK])


def rewrite_secrets(gathered_workers):
    existing_secrets = get_existing_credentials()
    final_result = 0
    for moniker, _ in gathered_workers.items():
        worker_app_secrets = get_moniker_app_secrets(moniker)
        worker_app_secrets['COS_WORKER_SECRET'] = existing_secrets[moniker]
        worker_test_secrets = get_moniker_test_secrets(moniker)
        worker_test_secrets['COS_WORKER_SECRET'] = existing_secrets[moniker]
        try:
            write_result_app_secrets = write_moniker_app_secrets(moniker, worker_app_secrets)
            write_result_test_secrets = write_moniker_test_secrets(moniker, worker_test_secrets)
            logging.info(str(write_result_app_secrets))
            logging.info(str(write_result_test_secrets))
        except hvac.exceptions.InvalidPath:
            cloudos_moniker = COS_MONIKER_MAP[APP_MONIKER]
            logging.critical(f"No Vault path to write secrets for moniker /{cloudos_moniker}/{moniker}/generic/appSecrets (or ../testSecrets) -- make certain that the moniker has been onboarded before writing secrets!")
            final_result = 1
    return final_result

async def main():
    parser = argparse.ArgumentParser(description='Write worker secrets to AWS Parameter Store and Vault.')
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--rotate', action='store_true', help='Rotate the parameter store keys, and write the current versions of the secrets into Vault')
    group.add_argument('--sync', action='store_true', help='Synchronize the secrets currently in parameter store into Vault')
    group.add_argument('--onboard', help='Create the COS_WORKER_SECRET in parameter store and Vault for a new moniker')
    args = parser.parse_args()

    if not is_vault_token_valid(VAULT_TOKEN):
        logging.fatal("Vault token is not authenticated. Do not run this script without a Vault token that can write the results to Vault.")
        return

    moniker = args.onboard if args.onboard else APP_MONIKER

    final_result = 0
    if args.sync:
        final_result = rewrite_secrets(gathered_workers)
    elif args.rotate or args.onboard:
        password = generate_random_password()
        logging.info('Moniker and secret: %s %s', moniker, password)
        write_secrets_and_labels(moniker, password)
        final_result = rewrite_secrets(moniker)
        logging.info('Done')
    return final_result

if __name__ ==  "__main__":
    logging.basicConfig(level=logging.DEBUG)
    # Execute only when run as a script
    EVENT_LOOP = asyncio.get_event_loop()
    try:
         result = EVENT_LOOP.run_until_complete(main())
         sys.exit(result)
    finally:
        EVENT_LOOP.close()    

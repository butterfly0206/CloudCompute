#!/usr/bin/env python3
import os
import sys
import boto3
import datetime
import json


def assume_role():
    role_arn = os.environ.get('ASSUME_ROLE_ARN', None)
    if role_arn:
        credentials = get_credentials(role_arn)
        if 'Error' in credentials:
            sys.exit(-1)
        return credentials


def get_credentials(role_arn: str) -> dict:
    # from https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-api.html
    try:
        sts_client = boto3.client('sts')
        response = sts_client.assume_role(
            RoleArn=role_arn,
            RoleSessionName="TestSession" + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
        )
        return response['Credentials']
    except Exception as e:
        sys.stderr.write(f'Failed to assume role: {e}')
        return {'Error': e}


def read_test_secrets() -> dict:
    if not os.environ.get('ASSUME_ROLE_ARN'):
        client = boto3.client('secretsmanager')
    else:
        # Assume Role needed to access Test Secrets in COSV3
        print("Assuming role for COSv3")
        credentials = assume_role()
        AccessKeyId = credentials.get('AccessKeyId', '')
        SecretAccessKey = credentials.get('SecretAccessKey', '')
        SessionToken = credentials.get('SessionToken', '')
        if not (AccessKeyId or SecretAccessKey or SessionToken):
            print("Failed to initialize AWS secrets")
            sys.exit(-1)

        client = boto3.client('secretsmanager', aws_access_key_id=AccessKeyId, aws_secret_access_key=SecretAccessKey,
                              aws_session_token=SessionToken)

    # The secret version will default to AWSCURRENT
    secret_version = "AWSCURRENT"

    # DEPLOYMENT_TYPE is either "app" or "test" in cloudos v3
    if os.environ.get("DEPLOYMENT_TYPE", "").lower() == "test":
        secret_version = "AWSPENDING"

    moniker = os.environ.get('APP_MONIKER', 'fpccomp-c-uw2')
    business_service = moniker.split('-')[0]
    secret_path = os.environ.get("COS_SECRETS_MANAGER_PATH", f"/{business_service}/test")

    print("Retrieving secret %s from AWS" % secret_path)
    response = client.get_secret_value(SecretId=secret_path, VersionStage=secret_version)

    if 'Error' in response:
        sys.stderr.write(f"{response['Error']}\n")
        sys.exit(1)

    parameter_map = json.loads(response['SecretString'])

    return parameter_map

#!/usr/bin/env bash
# shellcheck disable=SC2086
. "$(find /home/ctr-user/.local/share/virtualenvs/ -name 'activate')"
if [ -n "$SECRET_PATH_SUFFIX" ]; then
    # We are most likely running a test container, get the test secrets
    credentials="$(python3 /opt/cosv2AssumeRole.py)"
    parameters="$(env ${credentials} python3 /opt/cosv2ParameterStoreMappings.py)"
    secrets="$(env ${credentials} python3 /opt/cosv3SecretsManagerMappings.py)"

    exec env ${credentials} ${parameters} ${secrets} "$@"

elif [ -z "$AWS_CONTAINER_CREDENTIALS_RELATIVE_URI" ]; then
    # We are not running in ECS, forget the wrappers
    exec "$@"
else
    # We are running in ECS, include the wrappers
    parameters="$(python3 /opt/cosv2ParameterStoreMappings.py)"
    secrets="$(python3 /opt/cosv3SecretsManagerMappings.py)"
    ports="$(python3 /opt/cosv2PortMappings.py)"

    exec env ${parameters} ${secrets} ${ports} "$@"
fi

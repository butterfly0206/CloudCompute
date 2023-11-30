#!/usr/bin/env sh
. "$(find /home/ctr-user/.local/share/virtualenvs/ -name 'activate')"
if [ -z "$AWS_CONTAINER_CREDENTIALS_RELATIVE_URI" ]; then
  # We are not running in ECS, forget the wrappers
  exec python3 worker.py
else
  # shellcheck disable=SC2046
  exec env $(python3 ./cosv2ParameterStoreMappings.py) python3 helloworld-gpu.py
fi

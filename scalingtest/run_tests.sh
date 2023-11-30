#!/bin/bash
set -e

source $(pipenv --venv)/bin/activate

export TAR_NAME=test_results.tar.gz
export DEPLOY=rolling
export JOB_MANAGER_PORT=443
export WORKER_MANAGER_PORT=443
export JOB_MANAGER_HOST=job-manager.$APP_URL
export WORKER_MANAGER_HOST=worker-manager.$APP_URL


mkdir -p /docker-output

echo "APP_URL:  $APP_URL" | tee -a /docker-output/test.txt
echo "TAR_NAME:  $TAR_NAME" | tee -a /docker-output/test.txt
echo "JOB_MANAGER_HOST:  $JOB_MANAGER_HOST" | tee -a /docker-output/test.txt
echo "WORKER_MANAGER_HOST:  $WORKER_MANAGER_HOST" | tee -a /docker-output/test.txt
echo "JOB_MANAGER_PORT: $JOB_MANAGER_PORT" | tee -a /docker-output/test.txt
echo "WORKER_MANAGER_PORT: $WORKER_MANAGER_PORT" | tee -a /docker-output/test.txt

python ./tests.py
tar -zcvf $TAR_NAME /docker-output
mv $TAR_NAME /docker-output
mv test-results.xml /docker-output
exec "$@";

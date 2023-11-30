#!/bin/bash
set -e

if [[ "${COSV3}" == 'true' ]]; then
  export OUTPUT_DIR=/app/test-output
else
  export OUTPUT_DIR=/docker-output
fi
echo "Using $OUTPUT_DIR for test output"

mkdir -p $OUTPUT_DIR

export TAR_NAME=test_results.tar.gz
export DEPLOY=rolling
export JOB_MANAGER_PORT=443
export WORKER_MANAGER_PORT=443
export JOB_MANAGER_HOST=job-manager.$APP_URL
export WORKER_MANAGER_HOST=worker-manager.$APP_URL

echo "APP_URL:  $APP_URL" | tee -a $OUTPUT_DIR/test.txt
echo "TAR_NAME:  $TAR_NAME" | tee -a $OUTPUT_DIR/test.txt
echo "JOB_MANAGER_HOST:  $JOB_MANAGER_HOST" | tee -a $OUTPUT_DIR/test.txt
echo "WORKER_MANAGER_HOST:  $WORKER_MANAGER_HOST" | tee -a $OUTPUT_DIR/test.txt
echo "JOB_MANAGER_PORT: $JOB_MANAGER_PORT" | tee -a $OUTPUT_DIR/test.txt
echo "WORKER_MANAGER_PORT: $WORKER_MANAGER_PORT" | tee -a $OUTPUT_DIR/test.txt

python3 ./tests.py
exit_code=$?
tar -zcvf $TAR_NAME $OUTPUT_DIR
mv $TAR_NAME $OUTPUT_DIR
exit $exit_code

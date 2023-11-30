#!/bin/bash

export CSV_FILE=jmeter-test-plan.csv
export VAULT_TOKEN=$(cat ~/.vault-token)
export CTASKID=CTASK0123456
export BLAZEMETER_BASE_URL=https://a.blazemeter.com/api/v4
export BLAZEMETER_TEST_URL=${BLAZEMETER_BASE_URL}/tests/${TEST_ID}
export COS_API_URL=https://api.${COS_MONIKER}.autodesk.com
export GATEKEEPER_URL=https://${COS_MONIKER}.autodesk.com
export CLUSTER=$(echo $COS_MONIKER |  tr '[:lower:]' '[:upper:]')

function maxClusterSize {
   echo "Calling AWS to set MAX cluster size"
   aws ecs update-service --cluster ${CLUSTER} --service ${SERVICE}-${PORTFOLIO_VERSION_DASHES}-workermgr --desired-count 8 2>&1 > /dev/null
   aws ecs update-service --cluster ${CLUSTER} --service ${SERVICE}-${PORTFOLIO_VERSION_DASHES}-jobmanager --desired-count 8 2>&1 > /dev/null
}

function minClusterSize {
   echo "Calling AWS to set MIN cluster size"
   aws ecs update-service --cluster ${CLUSTER} --service ${SERVICE}-${PORTFOLIO_VERSION_DASHES}-workermgr --desired-count 3 2>&1 > /dev/null
   aws ecs update-service --cluster ${CLUSTER} --service ${SERVICE}-${PORTFOLIO_VERSION_DASHES}-jobmanager --desired-count 3 2>&1 > /dev/null
}

function getDeployedPortfolio {
    echo "Calling COS to get deployed portfolio #"
    echo curl -X GET "${GATEKEEPER_URL}/v1/apps/${SERVICE}/deployed" -H "X-Vault-Token: ${VAULT_TOKEN}"
    curl -X GET "${GATEKEEPER_URL}/v1/apps/${SERVICE}/deployed" -H "X-Vault-Token: ${VAULT_TOKEN}" -o response.json --silent
    export PORTFOLIO_VERSION=$(jq -r '.portfolioVersion' response.json)
    export PORTFOLIO_VERSION_DASHES=${PORTFOLIO_VERSION//\./-}
    echo "PORTFOLIO_VERSION=$PORTFOLIO_VERSION"
}

function restartComponent {
    echo "Calling COS to restart '$1' component"
    echo curl -X POST "${COS_API_URL}/v1/apps/${SERVICE}/components/$1/stopTasks?portfolio=${PORTFOLIO_VERSION}&slow=false&snowId=${CTASKID}" -H "X-Vault-Token: XXXXX"
    curl -X POST "${COS_API_URL}/v1/apps/${SERVICE}/components/$1/stopTasks?portfolio=${PORTFOLIO_VERSION}&slow=false&snowId=${CTASKID}" -H "X-Vault-Token: ${VAULT_TOKEN}" -o response.json --silent
    cat response.json
    echo ""
}

function syncSecret {  
    # Call COS to refresh the worker secret
    echo "Calling COS to sync deployed worker secret"
    echo curl -X POST "${COS_API_URL}/v1/apps/${SERVICE}/secrets?snowId=${CTASKID}" -d "{\"action\":\"refresh\",\"secretNames\":[\"COS_WORKER_SECRET\"]}"
    curl -X POST "${COS_API_URL}/v1/apps/${SERVICE}/secrets?snowId=${CTASKID}" -H "X-Vault-Token: ${VAULT_TOKEN}" -H "Content-Type: application/json" -d "{\"action\":\"refresh\",\"secretNames\":[\"COS_WORKER_SECRET\"]}" -o response.json --silent
    cat response.json
    echo ""

    restartComponent pworker
    restartComponent workermgr

    # Because I like to see progress
    echo "Sleeping 5m to let ECS restart the containers, current time ($(date -u))..."
    sleep 60
    echo "4m..."
    sleep 60
    echo "3m..."
    sleep 60
    echo "2m..."
    sleep 60
    echo "1m..."
    sleep 30
    echo "30s..."
    sleep 20
    echo "10s..."
    sleep 5
    echo "5s..."
    sleep 5
}

function rollSecret {
    # roll the worker secret
    echo "Rolling worker secrets"
    pipenv run python make_worker_credentials.py --onboard ${SERVICE} > response.json 2>&1

}

function cleanOldJobs {
    perfContainer=artifactory.dev.adskengineer.net/autodeskcloud/fpccomp-blazemeter-taurus:latest
    exec_cmd="docker run --rm -v $(pwd):/bzt-configs -v $(pwd)/target:/tmp/artifacts $perfContainer cleanup-blazemeter-jobs.yaml"
    echo $exec_cmd
    $exec_cmd
}

function uploadFile {
    echo "Uploading blazemeter test asset: $1"
    curl --user "${BLAZEMETER_API_ID}:${BLAZEMETER_API_SECRET}" -X POST ${BLAZEMETER_TEST_URL}/files -F "file=@$1" -o response.json --silent
}

function startTest {
    curl --user "${BLAZEMETER_API_ID}:${BLAZEMETER_API_SECRET}" -X POST ${BLAZEMETER_TEST_URL}/start -H 'Content-Type: application/json' -o response.json --silent
    export SESSION_ID=$(jq -r '.result.sessionsId[0]' response.json)
    echo "SESSION_ID=${SESSION_ID}"
}

function getSessionStatus {
    curl --user "${BLAZEMETER_API_ID}:${BLAZEMETER_API_SECRET}" -X GET ${BLAZEMETER_BASE_URL}/sessions/${SESSION_ID}/status -H 'Content-Type: application/json' -o response.json --silent
    export SESSION_STATUS=$(jq -r '.result.status' response.json)
    echo "SESSION_STATUS=${SESSION_STATUS}"
}

function runBlazemeter { 
    { #TRY-ish
        # send the JMX
        uploadFile $(pwd)/${JMX_FILE}
        # send the CSV (credentials)
        uploadFile $(pwd)/${CSV_FILE}

        echo "Starting blazemeter test"
        # start the test
        startTest
    } || { #CATCH-ish
        echo "Oops, failed to run blazemeter test"
    }
}

#!/bin/bash

# blazemeter account: 234972
# blazemeter workspace: 401308
# blazemetrer project: 648354

export TEST_ID=8137999
export JMX_FILE=autoscaling.jmx

# get the current credentials
cp ../../swagger/2.0/job-manager.yaml .
source ./generate-csv-c-sb.sh
source ./common.sh
pipenv install

#### MAIN ####
{
    #echo "Enter any text to skip the cleanup pass (will start in 30s otherwise)..."
    #read -t 30 -r REPLY
    #if [ -z $REPLY ]; then
    #    cleanOldJobs
    #fi
    getDeployedPortfolio
    # no messing with the cluster size
    runBlazemeter
    echo "Waiting for session ${SESSION_ID} to be ENDED"
    while [ "${SESSION_STATUS}" != "ENDED" ]
    do
    getSessionStatus
    sleep 30
    done
    # roll and refresh the worker secret twice time, to ensure what we used (and pushed to blazemeter) is invalidated
    rollSecret
    rollSecret
    syncSecret
    rm job-manager.yaml
    echo "Done with test session"
} || {
    echo "ERROR: Failed to complete testing session"
}
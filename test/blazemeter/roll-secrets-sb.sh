#!/bin/bash

# blazemeter account: 234972
# blazemeter workspace: 401308
# blazemetrer project: 533596
# blazemeter test: 7974993

# get the current credentials
cp ../../swagger/2.0/job-manager.yaml .
source ./generate-csv-c-sb.sh
source ./common.sh
pipenv install

#### MAIN ####
{
    getDeployedPortfolio
    rollSecret
    rollSecret
    syncSecret
    rm job-manager.yaml
    echo "Done with secrets rolling session"
} || {
    echo "ERROR: Failed to complete secrets rolling session"
}

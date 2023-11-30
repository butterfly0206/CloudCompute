#!/bin/bash

# blazemeter account: 234972
# blazemeter workspace: 401308
# blazemetrer project: 533596
# blazemeter test: 7974993

# get the current credentials
cp ../../swagger/3.0/job-manager.yaml .
source ./generate-csv-c-sb.sh
source ./common.sh

#### MAIN ####
{
    cleanOldJobs
    rm job-manager.yaml
    echo "Done with cleanup session"
} || {
    echo "ERROR: Failed to complete cleanup session"
}

#!/bin/bash

export csvFile=jmeter-test-plan.csv
echo "Generating ${csvFile}"
if [ -f ${csvFile} ]; then
  rm ${csvFile}
fi
if [ -d ./target/ ]; then
  rm -rf ./target/
fi

export VAULT_ADDR=https://civ1.dv.adskengineer.net:8200
export VAULT_SKIP_VERIFY=0

export AWS_REGION=us-west-2
export AWS_DEFAULT_REGION=us-west-2
export AWS_ACCOUNT=718144611626
echo $(vault write -format=json account/${AWS_ACCOUNT}/sts/Application-Ops ttl=240m \
|| vault write -format=json account/${AWS_ACCOUNT}/sts/Resource-Admin ttl=240m) > ~/.creds
export COS_MONIKER=cosv2-c-uw2-sb
export SERVICE=fpccomp-c-uw2-sb
export APP_MONIKER=fpccomp-c-uw2-sb
export PROTOCOL=https
export APIGEE_PREFIX=$SERVICE/jm
export APIGEE_HOST=developer-dev.api.autodesk.com
export JOB_MANAGER_HOST=$APIGEE_HOST
export JOB_MANAGER_PORT=
export WORKER_MANAGER_HOST=worker-manager.fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com
export WORKER_MANAGER_PORT=
export COS_WORKER_SECRET=$(vault read -field=COS_WORKER_SECRET /$COS_MONIKER/$SERVICE/generic/testSecrets | sed -e "s/,/\\\\,/g;") # escape commas for jmeter csv
export CLIENT_ID=$(vault read -field=OXYGEN_CLIENT_ID /$COS_MONIKER/$SERVICE/generic/appSecrets)
export CLIENT_SECRET=$(vault read -field=OXYGEN_CLIENT_SECRET /$COS_MONIKER/$SERVICE/generic/appSecrets)
export BLAZEMETER_API_ID=$(vault read -field=BLAZEMETER_API_ID /$COS_MONIKER/$SERVICE/generic/testSecrets)
export BLAZEMETER_API_SECRET=$(vault read -field=BLAZEMETER_API_SECRET /$COS_MONIKER/$SERVICE/generic/testSecrets)

json=$(curl -H "Content-Type: application/x-www-form-urlencoded;charset=utf-8" \
  -H "accept: application/json;charset=utf-8" -X POST \
  $PROTOCOL://$APIGEE_HOST/authentication/v1/authenticate -s \
  -d "client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&grant_type=client_credentials&scope=data:write")

export BEARER_TOKEN=$(echo $json | sed "s/{.*\"access_token\":\"\([^\"]*\).*}/\1/g" | sed -e "s/,/\\\\,/g;") # escape commas for jmeter csv

if [ -z "${BEARER_TOKEN}" ]; then
  echo "No bearer token"
  exit 1
fi

if [ -z "${COS_WORKER_SECRET}" ]; then
  echo "No worker secret"
  exit 1
fi

printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s" "$SERVICE" "$PROTOCOL" "$APIGEE_PREFIX" "$JOB_MANAGER_HOST" "$JOB_MANAGER_PORT" "$WORKER_MANAGER_HOST" "$WORKER_MANAGER_PORT" "$COS_WORKER_SECRET" "$BEARER_TOKEN" >> ${csvFile}

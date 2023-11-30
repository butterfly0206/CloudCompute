

copy /B swagger\3.0\job-manager-no-definitions.yaml + swagger\3.0\model-definitions.yaml swagger\3.0\job-manager.yaml

copy /Y .\.openapi-generator-ignore common\
copy /Y .\.openapi-generator-ignore job-manager\
copy /Y .\.openapi-generator-ignore worker-manager\
copy /Y .\.openapi-generator-ignore test-lambda\jmclient\

REM Generate the models in common
docker run --rm -v %cd%:/local -e JAVA_OPTS=-Dmodels openapitools/openapi-generator-cli:latest generate -i /local/swagger/3.0/job-manager.yaml -c /local/swagger/jm-swaggergen-config.json -g jaxrs-resteasy -o /local/common

REM Generate the APIs and supporting files in job-manager
docker run --rm -v %cd%:/local -e JAVA_OPTS="-Dapis -DsupportingFiles" openapitools/openapi-generator-cli:latest generate -i /local/swagger/3.0/job-manager.yaml -c /local/swagger/jm-swaggergen-config.json -g jaxrs-resteasy -o /local/job-manager

del common\.openapi-generator-ignore
del job-manager\.openapi-generator-ignore
del worker-manager\.openapi-generator-ignore
del test-lambda\jmclient\.openapi-generator-ignore

#!/usr/bin/env bash

# Assemble job-manager.yaml from its parts
rm -f swagger/3.0/job-manager.yaml || true
cat swagger/3.0/job-manager-no-definitions.yaml swagger/3.0/model-definitions.yaml >> swagger/3.0/job-manager.yaml
# Copy the ignore file to everywhere it's needed
./copy_ignore_files.sh

# First generate the models, then the APIs
docker run --rm -v $(pwd):/local \
  -e JAVA_OPTS=-Dmodels \
  openapitools/openapi-generator-cli:latest generate \
  -i /local/swagger/3.0/job-manager.yaml \
  -c /local/swagger/jm-swaggergen-config.json \
  -g jaxrs-resteasy \
  -o /local/common

docker run --rm -v $(pwd):/local \
  -e JAVA_OPTS="-Dapis -DsupportingFiles" \
  openapitools/openapi-generator-cli:latest generate \
  -i /local/swagger/3.0/job-manager.yaml \
  -c /local/swagger/jm-swaggergen-config.json \
  -g jaxrs-resteasy \
  -o /local/job-manager

# Clean up the ignore files
./cleanup_ignore_files.sh

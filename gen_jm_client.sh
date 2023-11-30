#!/usr/bin/env bash

# Assemble job-manager.yaml from its parts
rm -f swagger/3.0/job-manager.yaml || true
cat swagger/3.0/job-manager-no-definitions.yaml swagger/3.0/model-definitions.yaml >> swagger/3.0/job-manager.yaml
# Copy the ignore file to everywhere it's needed
./copy_ignore_files.sh

# Generate the client -- APIs only. The model is in common.
docker run --rm -v $(pwd):/local \
  -e JAVA_OPTS="-Dapis -DsupportingFiles" \
	openapitools/openapi-generator-cli:latest generate \
	-i /local/swagger/3.0/job-manager.yaml \
	-c /local/swagger/test-lambda-swaggergen-config.json \
	-g java \
	-o /local/test-lambda/jmclient

# Clean up the ignore files
./cleanup_ignore_files.sh

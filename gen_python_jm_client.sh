#!/usr/bin/env bash

# Assemble job-manager.yaml from its parts
rm -f swagger/3.0/job-manager.yaml || true
cat swagger/3.0/job-manager-no-definitions.yaml swagger/3.0/model-definitions.yaml >> swagger/3.0/job-manager.yaml
# Copy the ignore file to everywhere it's needed
./copy_ignore_files.sh
mkdir ./test/python/tmp/
cp -f .openapi-generator-ignore ./test/python/tmp
# Generate the client -- APIs only. The model is in common.
docker run --rm -v $(pwd):/local \
	openapitools/openapi-generator-cli:latest generate \
	-i /local/swagger/3.0/job-manager.yaml \
	-g python \
	--package-name "jmclient" \
  -o /local/test/python/tmp

cp -rf test/python/tmp/jmclient test/python/
rm -rf test/python/tmp

# Clean up the ignore files
./cleanup_ignore_files.sh

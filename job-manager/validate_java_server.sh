#!/usr/bin/env bash

docker run --rm -v $(pwd):/local \
	jimschubert/swagger-codegen-cli:2.2.3 validate \
	-i /local/api.yaml


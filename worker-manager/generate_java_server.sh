#!/usr/bin/env bash
docker run --rm -v $(pwd):/local \
  jimschubert/swagger-codegen-cli:2.3.1 generate \
  -i /local/api.yaml \
  -c /local/swaggergen-config.json \
  -l jaxrs-resteasy \
  -o /local


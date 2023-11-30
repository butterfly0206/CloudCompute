#!/usr/bin/env bash
docker run --rm -v $(pwd):/local \
	jimschubert/swagger-codegen-cli:2.3.1 generate \
	-i /local/api.yaml \
	-c /local/swaggergen-config.json \
	-l java \
	-o /local/client/

docker run --rm -v $(pwd):/local \
	jimschubert/swagger-codegen-cli:2.3.1 generate \
	-i /local/api.yaml \
	-c /local/swaggergen-config-csharp.json \
	-l csharp \
	-o /local/client-csharp/ \
  -DpackageName=Autodesk.Compute.WorkerManager
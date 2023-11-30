#!/usr/bin/env bash
mkdir -p ./public
docker run -v $(pwd)/..:/local sourcey/spectacle spectacle -t /local/public /local/swagger/3.0/worker-manager.yaml

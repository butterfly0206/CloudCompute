#!/usr/bin/env bash
docker run -it --rm -v "$PWD/java/server/.m2":/root/.m2 -v "$PWD/java/server":/usr/src/server -w /usr/src/server maven:3.2-jdk-7 mvn clean install

#!/usr/bin/env bash

# if you see something that shouldn't be used (like a beta or alpha); make sure to add it to rules.xml
mvn versions:use-latest-versions
mvn versions:update-properties
mvn package -DskipTests

date '+%Y/%m/%d %H:%M:%S' > last_updated_java_dependencies

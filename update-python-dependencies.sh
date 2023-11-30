#!/bin/sh

#currently runs no tests, must be tested carefully
(cd worker && pipenv update)

date '+%Y/%m/%d %H:%M:%S' >last_updated_worker_dependencies

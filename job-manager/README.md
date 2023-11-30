# compute-api
Autodesk's workflow manager for compute-style workloads

## How to build
	mvn clean install

## How to run
    Get the environment variables you need from <https://git.autodesk.com/cloud-platform/fpccomp-app/blob/master/CloudOS/fpccomp-c-uw2-sb.yaml>.
    These will include the list below, and many others.

	export AWS_PROFILE=cosv2-c-uw2-sb
	export AWS_REGION=us-west-2
	export AWS_DEFAULT_REGION=us-west-2
	java -cp target/jobmanager-1.0.0-jar-with-dependencies.jar:target/* com.autodesk.compute.jobmanager.boot.BootLocal

## How to test
	curl http://localhost:8090/api/v1/jobs to call the APIs.
	Swagger for the job manager and worker manager are at swagger/3.0/job-manager.yaml and swagger/3.0/worker-manager.yaml.
	There are unit tests all over; use `mvn clean install` to run them.
	The integration tests are written in Python, and located in tests/python.

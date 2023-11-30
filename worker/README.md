# CloudOS Compute Sample Application

This is a sample worker for the CloudOS Compute application.

It uses four APIs:

1.  Poll for a job
2.  Heartbeat while the job is progressing
3.  Report progress on the job
4.  Report success of the job

The important source code is in worker.py.

It generates the methods to call these APIs from the worker-manager YAML directly. This is a secondary source of the YAML!
The PRIMARY source of the yaml is the <https://git.autodesk.com/cloud-platform/fpccomp> repository in the swagger folder.

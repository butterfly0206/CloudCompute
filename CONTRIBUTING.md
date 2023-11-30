# Contributing to fpccomp

This document contains the requirements and guidelines for contributing to the CloudOS Compute code base, fpccomp. Please read this guide carefully.

For guidelines on building and testing locally, please refer to [README.md](README.md)

## Maintainers / Codeowners

-   @cloud-platform/stork

## Coding Standards

Most of the coding standards are enforced by SonarQube or Codacy, however, there are a few exceptions which are checked by code reviewers:

-   Prefer immutable over mutable objects
-   Proper usage of [Lombok](https://projectlombok.org/) over getters/setters
-   Proper usage of backoff, retries and jitter
-   Proper usage of functional constructs (optional, streams, functions)
-   Proper usage of comments; not too much, not too little
-   Function names are descriptive

## Unit Test

We have unit tests that need to pass. Code coverage in the unit tests is currently about 70%.

## Pull Request Standards

All work should be related to an associated JIRA ticket in the [FBSTORK](https://jira.autodesk.com/secure/RapidBoard.jspa?projectKey=FBSTORK) project. If your work is not yet associated with a JIRA ticket, please contact the Stork team to create and accept it. 

It is recommended that branches are named equal to on JIRA ticket, e,g, FBSTORK-12345, and Pull Request titles are prefixed the same way to ensure easy access to-from git-JIRA. 

A pull request is validated by our automated checks and reviewed by our team. 

Automation will check all of the following items:

-   Code can be compiled and all unit tests are passing
-   Chorus security testing is reporting no vulnerabilities
-   SonarQube quality gates are passing
-   Codacy quality gates are passing

Our team of reviewers will check:

-   All the relevant checklist items of the Pull Request template
-   Adds/adjusts unit tests related to new or modified API methods on job-manager or worker-manager, or gives a proper justification on why no new tests have been added
-   If deemed necessary, the reviewer might ask you to contribute with smoke tests
-   If deemed necessary, the reviewer might ask you to contribute with synthethic tests
-   The impact of the code to our SLIs in relation to our SLOs
-   If modifying an existing functionality; has there been any thought given to backwards compatibility?
-   If the request affects our threat model, we'lll follow up with a security assessment
-   If the request impacts operational elements, the reviewer might ask you to make adjustments to the runbook
-   Any additional 3rd party dependencies are properly justified and no viable already imported alternative exists 

Once all automations checks have passed, and a minimim of 1 code owner has reviewed and approved the PR, the reviewer will merge the code, the code will be deployed to our sandbox account and integration tests will run. If integration tests fail, the reviewer might rollback the changes and ask you to take look at the integration tests results before trying to merge the code again.

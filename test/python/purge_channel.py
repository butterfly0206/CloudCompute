# To run this script, you need config.py, also checked in here, and the following environment variables set:
# (You don't need most of these in here, but config.py has other things in it. To be self-contained, strip those out.)
# "AWS_PROFILE": "cosv2-c-uw2",
# "AWS_REGION": "us-west-2",
# "AWS_DEFAULT_REGION": "us-west-2",
# "APP_URL": "fpccomp-c-uw2.cosv2-c-uw2.autodesk.com",
# "ENVIRONMENT": "DEV",
# "SERVICE": "fpccomp-c-uw2",
# "WORKER": "sample1",
# "DEPLOY": "dns_blue_green",
# "APIGEE_REQUIRED": "true",
# "APIGEE_HOST": "developer-dev.api.autodesk.com",
# "APP_MONIKER": "fpccomp-c-uw2",
# "COS_PORTFOLIO_VERSION": "1.0.720"


import config
import sys


def poll_job(service, worker, portfolioVersion):
    PollData = config.WM_CLIENT.get_model('PollForJob')
    pollData = PollData(service=service, worker=worker, portfolioVersion=portfolioVersion)
    found_job = config.WM_CLIENT.poll.pollForJob(pollData=pollData,
                _request_options={
                    'timeout': 70
                }).result()
    if found_job[1].status_code == 404:
        return None
    return found_job[0]['jobID']


def delete_job(job_id):
    return config.JM_CLIENT.developers.deleteJob(id=job_id).result()


def main():
    found_job_id = poll_job('simsamp-c-uw2', 'mf2019_w', 'deployed')
    while found_job_id:
        _ = delete_job(found_job_id)
        found_job_id = poll_job('simsamp-c-uw2', 'mf2019_w', 'deployed')
    return 0


if __name__ == "__main__":
    result = main()
    sys.exit(result)


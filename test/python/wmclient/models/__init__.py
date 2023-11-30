# flake8: noqa

# import all models into this package
# if you have many models here with many references from one model to another this may
# raise a RecursionError
# to avoid this, import only the models that you directly need like:
# from from wmclient.model.pet import Pet
# or import this package, but before doing it, use:
# import sys
# sys.setrecursionlimit(n)

from wmclient.model.acquire_token_arguments import AcquireTokenArguments
from wmclient.model.array_job import ArrayJob
from wmclient.model.array_job_all_of import ArrayJobAllOf
from wmclient.model.array_job_args import ArrayJobArgs
from wmclient.model.array_job_args_all_of import ArrayJobArgsAllOf
from wmclient.model.array_job_item import ArrayJobItem
from wmclient.model.array_job_result import ArrayJobResult
from wmclient.model.array_job_result_all_of import ArrayJobResultAllOf
from wmclient.model.conclusion import Conclusion
from wmclient.model.conclusion_all_of import ConclusionAllOf
from wmclient.model.error import Error
from wmclient.model.failure import Failure
from wmclient.model.health_check_response import HealthCheckResponse
from wmclient.model.heartbeat import Heartbeat
from wmclient.model.heartbeat_response import HeartbeatResponse
from wmclient.model.job import Job
from wmclient.model.job_all_of import JobAllOf
from wmclient.model.job_args import JobArgs
from wmclient.model.job_args_all_of import JobArgsAllOf
from wmclient.model.job_info import JobInfo
from wmclient.model.job_progress import JobProgress
from wmclient.model.no_token_response import NoTokenResponse
from wmclient.model.poll_response import PollResponse
from wmclient.model.progress import Progress
from wmclient.model.progress_all_of import ProgressAllOf
from wmclient.model.result import Result
from wmclient.model.search_result import SearchResult
from wmclient.model.status import Status
from wmclient.model.status_update import StatusUpdate
from wmclient.model.success import Success
from wmclient.model.token_response import TokenResponse
from wmclient.model.worker import Worker

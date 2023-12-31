# flake8: noqa

"""
    CloudOS Compute Worker API

    Rest API for workers to pull jobs and report progress, liveness and completion. All APIs use a prefix of /api/v1.  # noqa: E501

    The version of the OpenAPI document: 1.0.18
    Contact: cloudos-compute@autodesk.com
    Generated by: https://openapi-generator.tech
"""


__version__ = "1.0.0"

# import ApiClient
from wmclient.api_client import ApiClient

# import Configuration
from wmclient.configuration import Configuration

# import exceptions
from wmclient.exceptions import OpenApiException
from wmclient.exceptions import ApiAttributeError
from wmclient.exceptions import ApiTypeError
from wmclient.exceptions import ApiValueError
from wmclient.exceptions import ApiKeyError
from wmclient.exceptions import ApiException

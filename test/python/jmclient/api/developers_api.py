"""
    CloudOS Compute API

    CloudOS Compute API for batch style workloads in Autodesk. Developers can register compute worker types by following the cloudOS2.0 onboarding process. Users can submit jobs against registered worker types. The system treats input and output as opaque JSON payloads that must confirm to the JSON schema specified by the worker documentation outside this system. All APIs are used with the /api/v1 prefix.   # noqa: E501

    The version of the OpenAPI document: 1.0.18
    Contact: cloudos-compute@autodesk.com
    Generated by: https://openapi-generator.tech
"""

import re  # noqa: F401
import sys  # noqa: F401
from jmclient.api_client import ApiClient, Endpoint as _Endpoint
from jmclient.model.array_job import ArrayJob
from jmclient.model.array_job_args import ArrayJobArgs
from jmclient.model.array_job_result import ArrayJobResult
from jmclient.model.job import Job
from jmclient.model.job_args import JobArgs
from jmclient.model.search_result import SearchResult
from jmclient.model_utils import (  # noqa: F401
    check_allowed_values,
    check_validations,
    date,
    datetime,
    file_type,
    none_type,
    validate_and_convert_types
)


class DevelopersApi(object):
    """NOTE: This class is auto generated by OpenAPI Generator
    Ref: https://openapi-generator.tech

    Do not edit the class manually.
    """

    def __init__(self, api_client=None):
        if api_client is None:
            api_client = ApiClient()
        self.api_client = api_client

        def __create_job(
                self,
                job_args,
                **kwargs
        ):
            """Creates a job in the system  # noqa: E501

            Creates a job for a particular compute worker type. Jobs are immutable from the client point of view once created and cannot be modified. A job will go through its own state machine and succeed or fail for various reasons including a worker-specific (defaults to 1 hour) or a worker no-longer heartbeating its progress (by default, required every 2 minutes). All jobs will be deleted from the system after 30 days of lifetime. Input payload for the job must comply with the JSON specification provided by the job worker developer.   # noqa: E501
            This method makes a synchronous HTTP request by default. To make an
            asynchronous HTTP request, please pass async_req=True

            >>> thread = api.create_job(job_args, async_req=True)
            >>> result = thread.get()

            Args:
                job_args (JobArgs): Job creation arguments

            Keyword Args:
                no_batch (bool): True to tell the Job Manager not to schedule a batch worker (e.g. so a test worker can pick it up via polling). [optional]
                _return_http_data_only (bool): response data without head status
                    code and headers. Default is True.
                _preload_content (bool): if False, the urllib3.HTTPResponse object
                    will be returned without reading/decoding response data.
                    Default is True.
                _request_timeout (float/tuple): timeout setting for this request. If one
                    number provided, it will be total request timeout. It can also
                    be a pair (tuple) of (connection, read) timeouts.
                    Default is None.
                _check_input_type (bool): specifies if type checking
                    should be done one the data sent to the server.
                    Default is True.
                _check_return_type (bool): specifies if type checking
                    should be done one the data received from the server.
                    Default is True.
                _host_index (int/None): specifies the index of the server
                    that we want to use.
                    Default is read from the configuration.
                async_req (bool): execute request asynchronously

            Returns:
                Job
                    If the method is called asynchronously, returns the request
                    thread.
            """
            kwargs['async_req'] = kwargs.get(
                'async_req', False
            )
            kwargs['_return_http_data_only'] = kwargs.get(
                '_return_http_data_only', True
            )
            kwargs['_preload_content'] = kwargs.get(
                '_preload_content', True
            )
            kwargs['_request_timeout'] = kwargs.get(
                '_request_timeout', None
            )
            kwargs['_check_input_type'] = kwargs.get(
                '_check_input_type', True
            )
            kwargs['_check_return_type'] = kwargs.get(
                '_check_return_type', True
            )
            kwargs['_host_index'] = kwargs.get('_host_index')
            kwargs['job_args'] = \
                job_args
            return self.call_with_http_info(**kwargs)

        self.create_job = _Endpoint(
            settings={
                'response_type': (Job,),
                'auth': [
                    'BearerAuth'
                ],
                'endpoint_path': '/jobs',
                'operation_id': 'create_job',
                'http_method': 'POST',
                'servers': None,
            },
            params_map={
                'all': [
                    'job_args',
                    'no_batch',
                ],
                'required': [
                    'job_args',
                ],
                'nullable': [
                ],
                'enum': [
                ],
                'validation': [
                ]
            },
            root_map={
                'validations': {
                },
                'allowed_values': {
                },
                'openapi_types': {
                    'job_args':
                        (JobArgs,),
                    'no_batch':
                        (bool,),
                },
                'attribute_map': {
                    'no_batch': 'noBatch',
                },
                'location_map': {
                    'job_args': 'body',
                    'no_batch': 'query',
                },
                'collection_format_map': {
                }
            },
            headers_map={
                'accept': [
                    'application/json'
                ],
                'content_type': [
                    'application/json'
                ]
            },
            api_client=api_client,
            callable=__create_job
        )

        def __create_jobs(
                self,
                job_args,
                **kwargs
        ):
            """Creates array of jobs in the system  # noqa: E501

            Creates array of jobs a particular compute worker type. Jobs are immutable from the client point of view once created and cannot be modified. A job will go through its own state machine and succeed or fail for various reasons including a worker-specific (defaults to 1 hour) or a worker no-longer heartbeating its progress (by default, required every 2 minutes). All jobs will be deleted from the system after 30 days of lifetime. Input payload for the job must comply with the JSON specification provided by the job worker developer.   # noqa: E501
            This method makes a synchronous HTTP request by default. To make an
            asynchronous HTTP request, please pass async_req=True

            >>> thread = api.create_jobs(job_args, async_req=True)
            >>> result = thread.get()

            Args:
                job_args (ArrayJobArgs): Array Job creation arguments

            Keyword Args:
                _return_http_data_only (bool): response data without head status
                    code and headers. Default is True.
                _preload_content (bool): if False, the urllib3.HTTPResponse object
                    will be returned without reading/decoding response data.
                    Default is True.
                _request_timeout (float/tuple): timeout setting for this request. If one
                    number provided, it will be total request timeout. It can also
                    be a pair (tuple) of (connection, read) timeouts.
                    Default is None.
                _check_input_type (bool): specifies if type checking
                    should be done one the data sent to the server.
                    Default is True.
                _check_return_type (bool): specifies if type checking
                    should be done one the data received from the server.
                    Default is True.
                _host_index (int/None): specifies the index of the server
                    that we want to use.
                    Default is read from the configuration.
                async_req (bool): execute request asynchronously

            Returns:
                ArrayJob
                    If the method is called asynchronously, returns the request
                    thread.
            """
            kwargs['async_req'] = kwargs.get(
                'async_req', False
            )
            kwargs['_return_http_data_only'] = kwargs.get(
                '_return_http_data_only', True
            )
            kwargs['_preload_content'] = kwargs.get(
                '_preload_content', True
            )
            kwargs['_request_timeout'] = kwargs.get(
                '_request_timeout', None
            )
            kwargs['_check_input_type'] = kwargs.get(
                '_check_input_type', True
            )
            kwargs['_check_return_type'] = kwargs.get(
                '_check_return_type', True
            )
            kwargs['_host_index'] = kwargs.get('_host_index')
            kwargs['job_args'] = \
                job_args
            return self.call_with_http_info(**kwargs)

        self.create_jobs = _Endpoint(
            settings={
                'response_type': (ArrayJob,),
                'auth': [
                    'BearerAuth'
                ],
                'endpoint_path': '/jobs/array',
                'operation_id': 'create_jobs',
                'http_method': 'POST',
                'servers': None,
            },
            params_map={
                'all': [
                    'job_args',
                ],
                'required': [
                    'job_args',
                ],
                'nullable': [
                ],
                'enum': [
                ],
                'validation': [
                ]
            },
            root_map={
                'validations': {
                },
                'allowed_values': {
                },
                'openapi_types': {
                    'job_args':
                        (ArrayJobArgs,),
                },
                'attribute_map': {
                },
                'location_map': {
                    'job_args': 'body',
                },
                'collection_format_map': {
                }
            },
            headers_map={
                'accept': [
                    'application/json'
                ],
                'content_type': [
                    'application/json'
                ]
            },
            api_client=api_client,
            callable=__create_jobs
        )

        def __delete_job(
                self,
                id,
                **kwargs
        ):
            """delete_job  # noqa: E501

            Deletes a single job for given job ID. A job can be deleted at any stage of its lifecycle. Since jobs are immutable, delete is synonymous to cancel and no separate cancel api is needed.   # noqa: E501
            This method makes a synchronous HTTP request by default. To make an
            asynchronous HTTP request, please pass async_req=True

            >>> thread = api.delete_job(id, async_req=True)
            >>> result = thread.get()

            Args:
                id (str): ID of job to delete

            Keyword Args:
                _return_http_data_only (bool): response data without head status
                    code and headers. Default is True.
                _preload_content (bool): if False, the urllib3.HTTPResponse object
                    will be returned without reading/decoding response data.
                    Default is True.
                _request_timeout (float/tuple): timeout setting for this request. If one
                    number provided, it will be total request timeout. It can also
                    be a pair (tuple) of (connection, read) timeouts.
                    Default is None.
                _check_input_type (bool): specifies if type checking
                    should be done one the data sent to the server.
                    Default is True.
                _check_return_type (bool): specifies if type checking
                    should be done one the data received from the server.
                    Default is True.
                _host_index (int/None): specifies the index of the server
                    that we want to use.
                    Default is read from the configuration.
                async_req (bool): execute request asynchronously

            Returns:
                None
                    If the method is called asynchronously, returns the request
                    thread.
            """
            kwargs['async_req'] = kwargs.get(
                'async_req', False
            )
            kwargs['_return_http_data_only'] = kwargs.get(
                '_return_http_data_only', True
            )
            kwargs['_preload_content'] = kwargs.get(
                '_preload_content', True
            )
            kwargs['_request_timeout'] = kwargs.get(
                '_request_timeout', None
            )
            kwargs['_check_input_type'] = kwargs.get(
                '_check_input_type', True
            )
            kwargs['_check_return_type'] = kwargs.get(
                '_check_return_type', True
            )
            kwargs['_host_index'] = kwargs.get('_host_index')
            kwargs['id'] = \
                id
            return self.call_with_http_info(**kwargs)

        self.delete_job = _Endpoint(
            settings={
                'response_type': None,
                'auth': [
                    'BearerAuth'
                ],
                'endpoint_path': '/jobs/{id}',
                'operation_id': 'delete_job',
                'http_method': 'DELETE',
                'servers': None,
            },
            params_map={
                'all': [
                    'id',
                ],
                'required': [
                    'id',
                ],
                'nullable': [
                ],
                'enum': [
                ],
                'validation': [
                ]
            },
            root_map={
                'validations': {
                },
                'allowed_values': {
                },
                'openapi_types': {
                    'id':
                        (str,),
                },
                'attribute_map': {
                    'id': 'id',
                },
                'location_map': {
                    'id': 'path',
                },
                'collection_format_map': {
                }
            },
            headers_map={
                'accept': [
                    'application/json'
                ],
                'content_type': [],
            },
            api_client=api_client,
            callable=__delete_job
        )

        def __get_job(
                self,
                id,
                **kwargs
        ):
            """get_job  # noqa: E501

            Returns a single job for given job ID  # noqa: E501
            This method makes a synchronous HTTP request by default. To make an
            asynchronous HTTP request, please pass async_req=True

            >>> thread = api.get_job(id, async_req=True)
            >>> result = thread.get()

            Args:
                id (str): ID of job to fetch

            Keyword Args:
                next_token (str): The list of jobs in array may be paginated and the nextToken is used to request the next page. The nextToken will be empty or null if there are no more pages. . [optional]
                _return_http_data_only (bool): response data without head status
                    code and headers. Default is True.
                _preload_content (bool): if False, the urllib3.HTTPResponse object
                    will be returned without reading/decoding response data.
                    Default is True.
                _request_timeout (float/tuple): timeout setting for this request. If one
                    number provided, it will be total request timeout. It can also
                    be a pair (tuple) of (connection, read) timeouts.
                    Default is None.
                _check_input_type (bool): specifies if type checking
                    should be done one the data sent to the server.
                    Default is True.
                _check_return_type (bool): specifies if type checking
                    should be done one the data received from the server.
                    Default is True.
                _host_index (int/None): specifies the index of the server
                    that we want to use.
                    Default is read from the configuration.
                async_req (bool): execute request asynchronously

            Returns:
                object
                    If the method is called asynchronously, returns the request
                    thread.
            """
            kwargs['async_req'] = kwargs.get(
                'async_req', False
            )
            kwargs['_return_http_data_only'] = kwargs.get(
                '_return_http_data_only', True
            )
            kwargs['_preload_content'] = kwargs.get(
                '_preload_content', True
            )
            kwargs['_request_timeout'] = kwargs.get(
                '_request_timeout', None
            )
            kwargs['_check_input_type'] = kwargs.get(
                '_check_input_type', True
            )
            kwargs['_check_return_type'] = kwargs.get(
                '_check_return_type', True
            )
            kwargs['_host_index'] = kwargs.get('_host_index')
            kwargs['id'] = id
            return self.call_with_http_info(**kwargs)

        self.get_job = _Endpoint(
            settings={
                'response_type': (Job, ArrayJobResult,),
                'auth': [
                    'BearerAuth'
                ],
                'endpoint_path': '/jobs/{id}',
                'operation_id': 'get_job',
                'http_method': 'GET',
                'servers': None,
            },
            params_map={
                'all': [
                    'id',
                    'next_token',
                ],
                'required': [
                    'id',
                ],
                'nullable': [
                ],
                'enum': [
                ],
                'validation': [
                ]
            },
            root_map={
                'validations': {
                },
                'allowed_values': {
                },
                'openapi_types': {
                    'id':
                        (str,),
                    'next_token':
                        (str,),
                },
                'attribute_map': {
                    'id': 'id',
                    'next_token': 'nextToken',
                },
                'location_map': {
                    'id': 'path',
                    'next_token': 'query',
                },
                'collection_format_map': {
                }
            },
            headers_map={
                'accept': [
                    'application/json'
                ],
                'content_type': [],
            },
            api_client=api_client,
            callable=__get_job
        )

        def __search_recent_jobs(
                self,
                service,
                max_results=100,
                **kwargs
        ):
            """Search recently (up to 30 days) modified jobs, regardless of status.  # noqa: E501

            Returns any recent job regardless of status within the time scope. The item order in the pages returned is arbitrary. A single query operation can retrieve a variable number of items, limited by the lesser of a maximum of 1 MB of data or maxResults (# of items per page).   # noqa: E501
            This method makes a synchronous HTTP request by default. To make an
            asynchronous HTTP request, please pass async_req=True

            >>> thread = api.search_recent_jobs(service, max_results=100, async_req=True)
            >>> result = thread.get()

            Args:
                service (str): Your appdef moniker
                max_results (int): Maximum number of results to return for each query page (may be limited still by aggregate data size <= 1 MB) . defaults to 100, must be one of [100]

            Keyword Args:
                from_time (str): Start searching from this time onwards, expressed in milliseconds since midnight January 1, 1970. . [optional]
                to_time (str): Start searching up to and including this time, expressed in milliseconds since midnight January 1, 1970. . [optional]
                tag (str): Filter your search results by this tag.. [optional]
                next_token (str): Internal token used for search pagination, returned in search results for queries which span multiple pages . [optional]
                _return_http_data_only (bool): response data without head status
                    code and headers. Default is True.
                _preload_content (bool): if False, the urllib3.HTTPResponse object
                    will be returned without reading/decoding response data.
                    Default is True.
                _request_timeout (float/tuple): timeout setting for this request. If one
                    number provided, it will be total request timeout. It can also
                    be a pair (tuple) of (connection, read) timeouts.
                    Default is None.
                _check_input_type (bool): specifies if type checking
                    should be done one the data sent to the server.
                    Default is True.
                _check_return_type (bool): specifies if type checking
                    should be done one the data received from the server.
                    Default is True.
                _host_index (int/None): specifies the index of the server
                    that we want to use.
                    Default is read from the configuration.
                async_req (bool): execute request asynchronously

            Returns:
                SearchResult
                    If the method is called asynchronously, returns the request
                    thread.
            """
            kwargs['async_req'] = kwargs.get(
                'async_req', False
            )
            kwargs['_return_http_data_only'] = kwargs.get(
                '_return_http_data_only', True
            )
            kwargs['_preload_content'] = kwargs.get(
                '_preload_content', True
            )
            kwargs['_request_timeout'] = kwargs.get(
                '_request_timeout', None
            )
            kwargs['_check_input_type'] = kwargs.get(
                '_check_input_type', True
            )
            kwargs['_check_return_type'] = kwargs.get(
                '_check_return_type', True
            )
            kwargs['_host_index'] = kwargs.get('_host_index')
            kwargs['service'] = \
                service
            kwargs['max_results'] = \
                max_results
            return self.call_with_http_info(**kwargs)

        self.search_recent_jobs = _Endpoint(
            settings={
                'response_type': (SearchResult,),
                'auth': [
                    'BearerAuth'
                ],
                'endpoint_path': '/search/recent',
                'operation_id': 'search_recent_jobs',
                'http_method': 'GET',
                'servers': None,
            },
            params_map={
                'all': [
                    'service',
                    'max_results',
                    'from_time',
                    'to_time',
                    'tag',
                    'next_token',
                ],
                'required': [
                    'service',
                    'max_results',
                ],
                'nullable': [
                ],
                'enum': [
                ],
                'validation': [
                ]
            },
            root_map={
                'validations': {
                },
                'allowed_values': {
                },
                'openapi_types': {
                    'service':
                        (str,),
                    'max_results':
                        (int,),
                    'from_time':
                        (str,),
                    'to_time':
                        (str,),
                    'tag':
                        (str,),
                    'next_token':
                        (str,),
                },
                'attribute_map': {
                    'service': 'service',
                    'max_results': 'maxResults',
                    'from_time': 'fromTime',
                    'to_time': 'toTime',
                    'tag': 'tag',
                    'next_token': 'nextToken',
                },
                'location_map': {
                    'service': 'query',
                    'max_results': 'query',
                    'from_time': 'query',
                    'to_time': 'query',
                    'tag': 'query',
                    'next_token': 'header',
                },
                'collection_format_map': {
                }
            },
            headers_map={
                'accept': [
                    'application/json'
                ],
                'content_type': [],
            },
            api_client=api_client,
            callable=__search_recent_jobs
        )

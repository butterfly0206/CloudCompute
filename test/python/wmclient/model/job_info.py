"""
    CloudOS Compute Worker API

    Rest API for workers to pull jobs and report progress, liveness and completion. All APIs use a prefix of /api/v1.  # noqa: E501

    The version of the OpenAPI document: 1.0.18
    Contact: cloudos-compute@autodesk.com
    Generated by: https://openapi-generator.tech
"""


import re  # noqa: F401
import sys  # noqa: F401

from wmclient.model_utils import (  # noqa: F401
    ApiTypeError,
    ModelComposed,
    ModelNormal,
    ModelSimple,
    cached_property,
    change_keys_js_to_python,
    convert_js_args_to_python_args,
    date,
    datetime,
    file_type,
    none_type,
    validate_get_composed_info,
)

def lazy_import():
    from wmclient.model.failure import Failure
    from wmclient.model.job_progress import JobProgress
    from wmclient.model.status import Status
    from wmclient.model.status_update import StatusUpdate
    globals()['Failure'] = Failure
    globals()['JobProgress'] = JobProgress
    globals()['Status'] = Status
    globals()['StatusUpdate'] = StatusUpdate


class JobInfo(ModelNormal):
    """NOTE: This class is auto generated by OpenAPI Generator.
    Ref: https://openapi-generator.tech

    Do not edit the class manually.

    Attributes:
      allowed_values (dict): The key is the tuple path to the attribute
          and the for var_name this is (var_name,). The value is a dict
          with a capitalized key describing the allowed value and an allowed
          value. These dicts store the allowed enum values.
      attribute_map (dict): The key is attribute name
          and the value is json key in definition.
      discriminator_value_class_map (dict): A dict to go from the discriminator
          variable value to the discriminator class name.
      validations (dict): The key is the tuple path to the attribute
          and the for var_name this is (var_name,). The value is a dict
          that stores validations for max_length, min_length, max_items,
          min_items, exclusive_maximum, inclusive_maximum, exclusive_minimum,
          inclusive_minimum, and regex.
      additional_properties_type (tuple): A tuple of classes accepted
          as additional properties values.
    """

    allowed_values = {
    }

    validations = {
        ('job_id',): {
            'regex': {
                'pattern': r'[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}(:array(:(0|([1-9]\d{0,3})))?)?',  # noqa: E501
            },
        },
    }

    additional_properties_type = None

    _nullable = False

    @cached_property
    def openapi_types():
        """
        This must be a method because a model may have properties that are
        of type self, this must run after the class is loaded

        Returns
            openapi_types (dict): The key is attribute name
                and the value is attribute type.
        """
        lazy_import()
        return {
            'job_id': (str,),  # noqa: E501
            'status': (Status,),  # noqa: E501
            'progress': (JobProgress,),  # noqa: E501
            'service_client_id': (str,),  # noqa: E501
            'creation_time': (str,),  # noqa: E501
            'modification_time': (str,),  # noqa: E501
            'errors': ([Failure],),  # noqa: E501
            'tags': ([str],),  # noqa: E501
            'payload': ({str: (bool, date, datetime, dict, float, int, list, str, none_type)},),  # noqa: E501
            'result': ({str: (bool, date, datetime, dict, float, int, list, str, none_type)},),  # noqa: E501
            'status_updates': ([StatusUpdate],),  # noqa: E501
        }

    @cached_property
    def discriminator():
        return None


    attribute_map = {
        'job_id': 'jobID',  # noqa: E501
        'status': 'status',  # noqa: E501
        'progress': 'progress',  # noqa: E501
        'service_client_id': 'serviceClientId',  # noqa: E501
        'creation_time': 'creationTime',  # noqa: E501
        'modification_time': 'modificationTime',  # noqa: E501
        'tags_modification_time': 'tagsModificationTime',  # noqa: E501
        'errors': 'errors',  # noqa: E501
        'tags': 'tags',  # noqa: E501
        'payload': 'payload',  # noqa: E501
        'result': 'result',  # noqa: E501
        'status_updates': 'statusUpdates',  # noqa: E501
    }

    _composed_schemas = {}

    required_properties = set([
        '_data_store',
        '_check_type',
        '_spec_property_naming',
        '_path_to_item',
        '_configuration',
        '_visited_composed_classes',
    ])

    @convert_js_args_to_python_args
    def __init__(self, *args, **kwargs):  # noqa: E501
        """JobInfo - a model defined in OpenAPI

        Keyword Args:
            _check_type (bool): if True, values for parameters in openapi_types
                                will be type checked and a TypeError will be
                                raised if the wrong type is input.
                                Defaults to True
            _path_to_item (tuple/list): This is a list of keys or values to
                                drill down to the model in received_data
                                when deserializing a response
            _spec_property_naming (bool): True if the variable names in the input data
                                are serialized names, as specified in the OpenAPI document.
                                False if the variable names in the input data
                                are pythonic names, e.g. snake case (default)
            _configuration (Configuration): the instance to use when
                                deserializing a file_type parameter.
                                If passed, type conversion is attempted
                                If omitted no type conversion is done.
            _visited_composed_classes (tuple): This stores a tuple of
                                classes that we have traveled through so that
                                if we see that class again we will not use its
                                discriminator again.
                                When traveling through a discriminator, the
                                composed schema that is
                                is traveled through is added to this set.
                                For example if Animal has a discriminator
                                petType and we pass in "Dog", and the class Dog
                                allOf includes Animal, we move through Animal
                                once using the discriminator, and pick Dog.
                                Then in Dog, we will make an instance of the
                                Animal class but this time we won't travel
                                through its discriminator because we passed in
                                _visited_composed_classes = (Animal,)
            job_id (str): [optional]  # noqa: E501
            status (Status): [optional]  # noqa: E501
            progress (JobProgress): [optional]  # noqa: E501
            service_client_id (str): service.clientId used as a search key for recent/archived jobs, this value is created internally using the Service plus the authorized client ID . [optional]  # noqa: E501
            creation_time (str): Indicates the time when the job record was created in ISO8601 format . [optional]  # noqa: E501
            modification_time (str): Indicates the time of the job record's last modification, expressed in milliseconds since midnight January 1, 1970. . [optional]  # noqa: E501
            errors ([Failure]): [optional]  # noqa: E501
            tags ([str]): [optional]  # noqa: E501
            payload ({str: (bool, date, datetime, dict, float, int, list, str, none_type)}): Arbitrary Json data, conforming to a schema associated with the application definition. [optional]  # noqa: E501
            result ({str: (bool, date, datetime, dict, float, int, list, str, none_type)}): Arbitrary Json data, conforming to a schema associated with the application definition. [optional]  # noqa: E501
            status_updates ([StatusUpdate]): [optional]  # noqa: E501
        """

        _check_type = kwargs.pop('_check_type', True)
        _spec_property_naming = kwargs.pop('_spec_property_naming', False)
        _path_to_item = kwargs.pop('_path_to_item', ())
        _configuration = kwargs.pop('_configuration', None)
        _visited_composed_classes = kwargs.pop('_visited_composed_classes', ())

        if args:
            raise ApiTypeError(
                "Invalid positional arguments=%s passed to %s. Remove those invalid positional arguments." % (
                    args,
                    self.__class__.__name__,
                ),
                path_to_item=_path_to_item,
                valid_classes=(self.__class__,),
            )

        self._data_store = {}
        self._check_type = _check_type
        self._spec_property_naming = _spec_property_naming
        self._path_to_item = _path_to_item
        self._configuration = _configuration
        self._visited_composed_classes = _visited_composed_classes + (self.__class__,)

        for var_name, var_value in kwargs.items():
            if var_name not in self.attribute_map and \
                        self._configuration is not None and \
                        self._configuration.discard_unknown_keys and \
                        self.additional_properties_type is None:
                # discard variable.
                continue
            setattr(self, var_name, var_value)

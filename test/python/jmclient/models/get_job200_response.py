# coding: utf-8

"""
    CloudOS Compute API

    CloudOS Compute API for batch style workloads in Autodesk. Developers can register compute worker types by following the cloudOS2.0 onboarding process. Users can submit jobs against registered worker types. The system treats input and output as opaque JSON payloads that must confirm to the JSON schema specified by the worker documentation outside this system. All APIs are used with the /api/v1 prefix. 

    The version of the OpenAPI document: 1.0.19
    Contact: cloudos-compute@autodesk.com
    Generated by OpenAPI Generator (https://openapi-generator.tech)

    Do not edit the class manually.
"""  # noqa: E501


from __future__ import annotations
from inspect import getfullargspec
import json
import pprint
import re  # noqa: F401

from typing import Any, List, Optional
from pydantic import BaseModel, Field, StrictStr, ValidationError, field_validator
from jmclient.models.array_job import ArrayJob
from jmclient.models.job import Job
from typing import Union, Any, List, TYPE_CHECKING, Optional, Dict
from typing_extensions import Literal
from pydantic import StrictStr, Field
try:
    from typing import Self
except ImportError:
    from typing_extensions import Self

GETJOB200RESPONSE_ONE_OF_SCHEMAS = ["ArrayJob", "Job"]

class GetJob200Response(BaseModel):
    """
    GetJob200Response
    """
    # data type: Job
    oneof_schema_1_validator: Optional[Job] = None
    # data type: ArrayJob
    oneof_schema_2_validator: Optional[ArrayJob] = None
    actual_instance: Optional[Union[ArrayJob, Job]] = None
    one_of_schemas: List[str] = Literal["ArrayJob", "Job"]

    model_config = {
        "validate_assignment": True
    }


    def __init__(self, *args, **kwargs) -> None:
        if args:
            if len(args) > 1:
                raise ValueError("If a position argument is used, only 1 is allowed to set `actual_instance`")
            if kwargs:
                raise ValueError("If a position argument is used, keyword arguments cannot be used.")
            super().__init__(actual_instance=args[0])
        else:
            super().__init__(**kwargs)

    @field_validator('actual_instance')
    def actual_instance_must_validate_oneof(cls, v):
        instance = GetJob200Response.model_construct()
        error_messages = []
        match = 0
        # validate data type: Job
        if not isinstance(v, Job):
            error_messages.append(f"Error! Input type `{type(v)}` is not `Job`")
        else:
            match += 1
        # validate data type: ArrayJob
        if not isinstance(v, ArrayJob):
            error_messages.append(f"Error! Input type `{type(v)}` is not `ArrayJob`")
        else:
            match += 1
        if match > 1:
            # more than 1 match
            raise ValueError("Multiple matches found when setting `actual_instance` in GetJob200Response with oneOf schemas: ArrayJob, Job. Details: " + ", ".join(error_messages))
        elif match == 0:
            # no match
            raise ValueError("No match found when setting `actual_instance` in GetJob200Response with oneOf schemas: ArrayJob, Job. Details: " + ", ".join(error_messages))
        else:
            return v

    @classmethod
    def from_dict(cls, obj: dict) -> Self:
        return cls.from_json(json.dumps(obj))

    @classmethod
    def from_json(cls, json_str: str) -> Self:
        """Returns the object represented by the json string"""
        instance = cls.model_construct()
        error_messages = []
        match = 0

        # deserialize data into Job
        try:
            instance.actual_instance = Job.from_json(json_str)
            match += 1
        except (ValidationError, ValueError) as e:
            error_messages.append(str(e))
        # deserialize data into ArrayJob
        try:
            instance.actual_instance = ArrayJob.from_json(json_str)
            match += 1
        except (ValidationError, ValueError) as e:
            error_messages.append(str(e))

        if match > 1:
            # more than 1 match
            raise ValueError("Multiple matches found when deserializing the JSON string into GetJob200Response with oneOf schemas: ArrayJob, Job. Details: " + ", ".join(error_messages))
        elif match == 0:
            # no match
            raise ValueError("No match found when deserializing the JSON string into GetJob200Response with oneOf schemas: ArrayJob, Job. Details: " + ", ".join(error_messages))
        else:
            return instance

    def to_json(self) -> str:
        """Returns the JSON representation of the actual instance"""
        if self.actual_instance is None:
            return "null"

        to_json = getattr(self.actual_instance, "to_json", None)
        if callable(to_json):
            return self.actual_instance.to_json()
        else:
            return json.dumps(self.actual_instance)

    def to_dict(self) -> dict:
        """Returns the dict representation of the actual instance"""
        if self.actual_instance is None:
            return None

        to_dict = getattr(self.actual_instance, "to_dict", None)
        if callable(to_dict):
            return self.actual_instance.to_dict()
        else:
            # primitive type
            return self.actual_instance

    def to_str(self) -> str:
        """Returns the string representation of the actual instance"""
        return pprint.pformat(self.model_dump())



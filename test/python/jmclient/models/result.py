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
import pprint
import re  # noqa: F401
import json


from typing import Any, Dict, Optional, Union
from pydantic import BaseModel, StrictStr
from pydantic import Field
from typing import Dict, Any
try:
    from typing import Self
except ImportError:
    from typing_extensions import Self

class Result(BaseModel):
    """
    Result
    """
    result: Optional[Union[str, Any]] = None
    error: Optional[StrictStr] = Field(default=None, description="Error enum or a short description of the error")
    details: Optional[Union[str, Any]] = Field(default=None, description="Any specific details around the error thrown. These will be provided to the client ")
    timestamp: Optional[StrictStr] = Field(default=None, description="The ISO8601 timestamp when the error occured.")
    __properties: ClassVar[List[str]] = ["result", "error", "details", "timestamp"]

    model_config = {
        "populate_by_name": True,
        "validate_assignment": True
    }


    def to_str(self) -> str:
        """Returns the string representation of the model using alias"""
        return pprint.pformat(self.model_dump(by_alias=True))

    def to_json(self) -> str:
        """Returns the JSON representation of the model using alias"""
        # TODO: pydantic v2: use .model_dump_json(by_alias=True, exclude_unset=True) instead
        return json.dumps(self.to_dict())

    @classmethod
    def from_json(cls, json_str: str) -> Self:
        """Create an instance of Result from a JSON string"""
        return cls.from_dict(json.loads(json_str))

    def to_dict(self):
        """Returns the dictionary representation of the model using alias"""
        _dict = self.model_dump(by_alias=True,
                          exclude={
                          },
                          exclude_none=True)
        return _dict

    @classmethod
    def from_dict(cls, obj: dict) -> Self:
        """Create an instance of Result from a dict"""
        if obj is None:
            return None

        if not isinstance(obj, dict):
            return cls.model_validate(obj)

        _obj = cls.model_validate({
            "result": obj.get("result"),
            "error": obj.get("error"),
            "details": obj.get("details"),
            "timestamp": obj.get("timestamp")
        })
        return _obj



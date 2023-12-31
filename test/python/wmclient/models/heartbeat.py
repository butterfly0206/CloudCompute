# coding: utf-8

"""
    CloudOS Compute Worker API

    Rest API for workers to pull jobs and report progress, liveness and completion. All APIs use a prefix of /api/v1.

    The version of the OpenAPI document: 1.0.18
    Contact: cloudos-compute@autodesk.com
    Generated by OpenAPI Generator (https://openapi-generator.tech)

    Do not edit the class manually.
"""  # noqa: E501


from __future__ import annotations
import pprint
import re  # noqa: F401
import json



from pydantic import BaseModel, StrictStr, field_validator
from pydantic import Field
from typing_extensions import Annotated
from typing import Dict, Any
try:
    from typing import Self
except ImportError:
    from typing_extensions import Self

class Heartbeat(BaseModel):
    """
    Heartbeat
    """
    job_id: Annotated[str, Field(strict=True)] = Field(alias="jobID")
    job_secret: StrictStr = Field(alias="jobSecret")
    __properties: ClassVar[List[str]] = ["jobID", "jobSecret"]

    @field_validator('job_id')
    def job_id_validate_regular_expression(cls, value):
        """Validates the regular expression"""
        if not re.match(r"[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}(:array(:(0|([1-9]\d{0,3})))?)?", value):
            raise ValueError(r"must validate the regular expression /[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}(:array(:(0|([1-9]\d{0,3})))?)?/")
        return value

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
        """Create an instance of Heartbeat from a JSON string"""
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
        """Create an instance of Heartbeat from a dict"""
        if obj is None:
            return None

        if not isinstance(obj, dict):
            return cls.model_validate(obj)

        _obj = cls.model_validate({
            "jobID": obj.get("jobID"),
            "jobSecret": obj.get("jobSecret")
        })
        return _obj



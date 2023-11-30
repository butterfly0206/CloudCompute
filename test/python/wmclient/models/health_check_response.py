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


from typing import Optional
from pydantic import BaseModel, StrictStr, field_validator
from pydantic import Field
from typing import Dict, Any
try:
    from typing import Self
except ImportError:
    from typing_extensions import Self

class HealthCheckResponse(BaseModel):
    """
    HealthCheckResponse
    """
    portfolio_version: Optional[StrictStr] = Field(default=None, description="The portfolio version of the worker", alias="portfolioVersion")
    overall: StrictStr = Field(description="The overall health of the compute service (worker manager or job manager)")
    scan_time: StrictStr = Field(description="The ISO8601 timestamp representing the start of the healthcheck", alias="scanTime")
    revision: Optional[StrictStr] = Field(default=None, description="Current build version\\")
    __properties: ClassVar[List[str]] = ["portfolioVersion", "overall", "scanTime", "revision"]

    @field_validator('overall')
    def overall_validate_enum(cls, value):
        """Validates the enum"""
        if value not in ('HEALTHY', 'UNHEALTHY', 'DEGRADED'):
            raise ValueError("must be one of enum values ('HEALTHY', 'UNHEALTHY', 'DEGRADED')")
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
        """Create an instance of HealthCheckResponse from a JSON string"""
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
        """Create an instance of HealthCheckResponse from a dict"""
        if obj is None:
            return None

        if not isinstance(obj, dict):
            return cls.model_validate(obj)

        _obj = cls.model_validate({
            "portfolioVersion": obj.get("portfolioVersion"),
            "overall": obj.get("overall"),
            "scanTime": obj.get("scanTime"),
            "revision": obj.get("revision")
        })
        return _obj



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


from typing import List, Optional
from pydantic import BaseModel, StrictStr
from pydantic import Field
from typing_extensions import Annotated
from jmclient.models.array_job_item import ArrayJobItem
from typing import Dict, Any
try:
    from typing import Self
except ImportError:
    from typing_extensions import Self

class ArrayJobArgs(BaseModel):
    """
    ArrayJobArgs
    """
    service: StrictStr = Field(description="An appdef moniker")
    worker: StrictStr = Field(description="The particular worker within a service")
    portfolio_version: Optional[StrictStr] = Field(default=None, description="The portfolio version of the worker", alias="portfolioVersion")
    jobs: Optional[Annotated[List[ArrayJobItem], Field(min_length=2, max_length=10000)]] = None
    __properties: ClassVar[List[str]] = ["service", "worker", "portfolioVersion", "jobs"]

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
        """Create an instance of ArrayJobArgs from a JSON string"""
        return cls.from_dict(json.loads(json_str))

    def to_dict(self):
        """Returns the dictionary representation of the model using alias"""
        _dict = self.model_dump(by_alias=True,
                          exclude={
                          },
                          exclude_none=True)
        # override the default output from pydantic by calling `to_dict()` of each item in jobs (list)
        _items = []
        if self.jobs:
            for _item in self.jobs:
                if _item:
                    _items.append(_item.to_dict())
            _dict['jobs'] = _items
        return _dict

    @classmethod
    def from_dict(cls, obj: dict) -> Self:
        """Create an instance of ArrayJobArgs from a dict"""
        if obj is None:
            return None

        if not isinstance(obj, dict):
            return cls.model_validate(obj)

        _obj = cls.model_validate({
            "service": obj.get("service"),
            "worker": obj.get("worker"),
            "portfolioVersion": obj.get("portfolioVersion"),
            "jobs": [ArrayJobItem.from_dict(_item) for _item in obj.get("jobs")] if obj.get("jobs") is not None else None
        })
        return _obj



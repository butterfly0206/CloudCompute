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


from typing import List
from pydantic import BaseModel, StrictStr, field_validator
from pydantic import Field
from typing_extensions import Annotated
from jmclient.models.job import Job
from typing import Dict, Any
try:
    from typing import Self
except ImportError:
    from typing_extensions import Self

class SearchResult(BaseModel):
    """
    Data returned from a search   # noqa: E501
    """
    jobs: List[Job]
    last_update_time: StrictStr = Field(description="Time from the last database update. Use it as the \"fromTime\" in the next search. Expressed in milliseconds since midnight January 1, 1970. ", alias="lastUpdateTime")
    next_token: Annotated[str, Field(strict=True)] = Field(description="Internal token used for search pagination, returned in search results for queries which span multiple pages ", alias="nextToken")
    __properties: ClassVar[List[str]] = ["jobs", "lastUpdateTime", "nextToken"]

    @field_validator('next_token')
    def next_token_validate_regular_expression(cls, value):
        """Validates the regular expression"""
        if not re.match(r"^(?:[A-Za-z0-9+\/]{4})*(?:[A-Za-z0-9+\/]{2}==|[A-Za-z0-9+\/]{3}=)?$", value):
            raise ValueError(r"must validate the regular expression /^(?:[A-Za-z0-9+\/]{4})*(?:[A-Za-z0-9+\/]{2}==|[A-Za-z0-9+\/]{3}=)?$/")
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
        """Create an instance of SearchResult from a JSON string"""
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
        """Create an instance of SearchResult from a dict"""
        if obj is None:
            return None

        if not isinstance(obj, dict):
            return cls.model_validate(obj)

        _obj = cls.model_validate({
            "jobs": [Job.from_dict(_item) for _item in obj.get("jobs")] if obj.get("jobs") is not None else None,
            "lastUpdateTime": obj.get("lastUpdateTime"),
            "nextToken": obj.get("nextToken")
        })
        return _obj



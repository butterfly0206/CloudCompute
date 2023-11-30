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


from typing import Any, Dict, List, Optional, Union
from pydantic import BaseModel, StrictStr, field_validator
from pydantic import Field
from typing_extensions import Annotated
from jmclient.models.failure import Failure
from jmclient.models.job_progress import JobProgress
from jmclient.models.status import Status
from jmclient.models.status_update import StatusUpdate
from typing import Dict, Any
try:
    from typing import Self
except ImportError:
    from typing_extensions import Self

class Job(BaseModel):
    """
    Job
    """
    service: StrictStr = Field(description="An appdef moniker")
    worker: StrictStr = Field(description="The particular worker within a service")
    portfolio_version: Optional[StrictStr] = Field(default=None, description="The portfolio version of the worker", alias="portfolioVersion")
    job_id: Optional[Annotated[str, Field(strict=True)]] = Field(default=None, alias="jobID")
    status: Optional[Status] = None
    progress: Optional[JobProgress] = None
    service_client_id: Optional[StrictStr] = Field(default=None, description="service.clientId used as a search key for recent/archived jobs, this value is created internally using the Service plus the authorized client ID ", alias="serviceClientId")
    creation_time: Optional[StrictStr] = Field(default=None, description="Indicates the time when the job record was created in ISO8601 format ", alias="creationTime")
    modification_time: Optional[StrictStr] = Field(default=None, description="Indicates the time of the job record's last modification, expressed in milliseconds since midnight January 1, 1970. ", alias="modificationTime")
    tags_modification_time: Optional[StrictStr] = Field(default=None, description="Indicates the time of the job tags's last modification, expressed in milliseconds since midnight January 1, 1970. ", alias="tagsModificationTime")
    errors: Optional[List[Failure]] = None
    tags: Optional[List[StrictStr]] = None
    payload: Optional[Union[str, Any]] = Field(default=None, description="Arbitrary Json data, conforming to a schema associated with the application definition")
    result: Optional[Union[str, Any]] = Field(default=None, description="Arbitrary Json data, conforming to a schema associated with the application definition")
    status_updates: Optional[List[StatusUpdate]] = Field(default=None, alias="statusUpdates")
    type: Optional[StrictStr] = 'Job'
    __properties: ClassVar[List[str]] = ["service", "worker", "portfolioVersion", "jobID", "status", "progress", "serviceClientId", "creationTime", "modificationTime", "tagsModificationTime", "errors", "tags", "payload", "result", "statusUpdates", "type"]

    @field_validator('job_id')
    def job_id_validate_regular_expression(cls, value):
        """Validates the regular expression"""
        if value is None:
            return value

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
        """Create an instance of Job from a JSON string"""
        return cls.from_dict(json.loads(json_str))

    def to_dict(self):
        """Returns the dictionary representation of the model using alias"""
        _dict = self.model_dump(by_alias=True,
                          exclude={
                          },
                          exclude_none=True)
        # override the default output from pydantic by calling `to_dict()` of progress
        if self.progress:
            _dict['progress'] = self.progress.to_dict()
        # override the default output from pydantic by calling `to_dict()` of each item in errors (list)
        _items = []
        if self.errors:
            for _item in self.errors:
                if _item:
                    _items.append(_item.to_dict())
            _dict['errors'] = _items
        # override the default output from pydantic by calling `to_dict()` of each item in status_updates (list)
        _items = []
        if self.status_updates:
            for _item in self.status_updates:
                if _item:
                    _items.append(_item.to_dict())
            _dict['statusUpdates'] = _items
        return _dict

    @classmethod
    def from_dict(cls, obj: dict) -> Self:
        """Create an instance of Job from a dict"""
        if obj is None:
            return None

        if not isinstance(obj, dict):
            return cls.model_validate(obj)

        _obj = cls.model_validate({
            "service": obj.get("service"),
            "worker": obj.get("worker"),
            "portfolioVersion": obj.get("portfolioVersion"),
            "jobID": obj.get("jobID"),
            "status": obj.get("status"),
            "progress": JobProgress.from_dict(obj.get("progress")) if obj.get("progress") is not None else None,
            "serviceClientId": obj.get("serviceClientId"),
            "creationTime": obj.get("creationTime"),
            "modificationTime": obj.get("modificationTime"),
            "tagsModificationTime": obj.get("tagsModificationTime"),
            "errors": [Failure.from_dict(_item) for _item in obj.get("errors")] if obj.get("errors") is not None else None,
            "tags": obj.get("tags"),
            "payload": obj.get("payload"),
            "result": obj.get("result"),
            "statusUpdates": [StatusUpdate.from_dict(_item) for _item in obj.get("statusUpdates")] if obj.get("statusUpdates") is not None else None,
            "type": obj.get("type") if obj.get("type") is not None else 'Job'
        })
        return _obj



import datetime
import logging
import os
import re
from typing import List

import boto3

SFN = boto3.client('stepfunctions')

IS_PORTFOLIO_VERSIONED_REGEX = re.compile('-\\d+[-_]\\d+[-_]\\d+[-_]')

OLD_TIME_IN_DAYS = int(os.environ.get('MAX_AGE_DAYS', '90'))

OLD_TIME = datetime.datetime.utcnow() - datetime.timedelta(days=OLD_TIME_IN_DAYS)


def is_portfolio_versioned(arn: str) -> bool:
    return IS_PORTFOLIO_VERSIONED_REGEX.search(arn) is not None


def is_old(timestamp: datetime.datetime):
    return OLD_TIME > timestamp.replace(tzinfo=None)


def can_delete_state_machine(state_machine: dict) -> bool:
    return 'fpcc' in state_machine['stateMachineArn'] and is_portfolio_versioned(state_machine['stateMachineArn']) and is_old(state_machine['creationDate'])


def can_delete_activity(activity: dict) -> bool:
    return ('FPCC' in activity['activityArn'] or 'fpcc' in activity['activityArn']) and is_portfolio_versioned(activity['activityArn']) and is_old(activity['creationDate'])


def get_paginated(api_name: str, array_property: str) -> List[dict]:
    items: List[dict] = []
    item_paginator = SFN.get_paginator(api_name)
    for one_page in item_paginator.paginate():
        items.extend([item for item in one_page[array_property]])
    return items


def get_state_machines() -> List[dict]:
    return get_paginated('list_state_machines', 'stateMachines')


def get_activities() -> List[dict]:
    return get_paginated('list_activities', 'activities')


def delete_state_machines(arns: List[str]):
    for one_arn in arns:
        SFN.delete_state_machine(stateMachineArn=one_arn)


def delete_activities(arns: List[str]):
    for one_arn in arns:
        SFN.delete_activity(activityArn=one_arn)


def main():
    # The Lambda environment pre-configures a handler logging to stderr. If a handler is already configured,
    # `.basicConfig` does not execute. Thus we set the level directly.
    if len(logging.getLogger().handlers) > 0:
        logging.getLogger().setLevel(logging.INFO)
    else:
        logging.basicConfig(level=logging.INFO)
    state_machines = get_state_machines()
    arns_to_delete = [item['stateMachineArn'] for item in state_machines if can_delete_state_machine(item)]
    logging.info('Found {} state machines; preparing to delete {} of them'.format(len(state_machines), len(arns_to_delete)))
    delete_state_machines(arns_to_delete)
    logging.info('Finished with state machines; starting activities')
    activities = get_activities()
    arns_to_delete = [item['activityArn'] for item in activities if can_delete_activity(item)]
    logging.info('Found {} activities; preparing to delete {} of them'.format(len(activities), len(arns_to_delete)))
    delete_activities(arns_to_delete)
    logging.info('Finished with activities')


def lambda_handler(event, context):
    if "Healthcheck" in event.keys():
        return "HealthcheckPassed!"
    else:
        main()


if __name__ == "__main__":
    main()
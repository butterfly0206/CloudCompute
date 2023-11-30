import boto3
import sys

if len(sys.argv) <= 1:
    print("Not enough parameters provided. Please provide the table name")
    print("Usage: AWS_PROFILE=cosv2-c-uw2-sb AWS_DEFAULT_REGION=us-west-2 python updateServiceClientId.py cosv2_c_uw2_sb_fpc_job_progress_data")
    exit(1)

print("The script will scan the entire table and update only the items that haven't been updated yet")

tableName = sys.argv[1]

dynamodb = boto3.client('dynamodb')

response = dynamodb.scan(
    TableName=tableName,
    AttributesToGet=[
        'JobId', "ServiceClientId"
    ]
)

while True:
    lastEvaluatedKey = response['LastEvaluatedKey']['JobId']['S'] if response.get('LastEvaluatedKey') else None
    items = response['Items']

    for item in items:
        JobId = item['JobId']['S']
        ServiceClientId = item['ServiceClientId']['S'] if item.get('ServiceClientId') else None

        if ServiceClientId and '.' in ServiceClientId:

            newServiceClientId = next(iter(filter(None, ServiceClientId.split(".")[::-1])), 'None')
            print("Updating %s(%s) and replacing with (%s)" % (JobId, ServiceClientId, newServiceClientId))
            response = dynamodb.update_item(
                TableName=tableName,
                Key={
                    'JobId': {
                        'S': JobId
                    }
                },
                ReturnValues='NONE',
                ReturnConsumedCapacity='NONE',
                ReturnItemCollectionMetrics='NONE',
                UpdateExpression='SET ServiceClientId = :serviceClientID',
                ExpressionAttributeValues={
                    ':serviceClientID': {
                        'S': newServiceClientId
                    }
                }
            )

    if lastEvaluatedKey is None:
        break

    response = dynamodb.scan(
        TableName=tableName,
        AttributesToGet=[
            'JobId', "ServiceClientId"
        ],
        ExclusiveStartKey={
            'JobId': {
                'S': lastEvaluatedKey
            }
        },
    )


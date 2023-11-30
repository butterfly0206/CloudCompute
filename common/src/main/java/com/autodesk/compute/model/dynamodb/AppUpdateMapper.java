package com.autodesk.compute.model.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Builder;

@Builder
@DynamoDBTable(tableName = "SNSUpdates")
public class AppUpdateMapper {
    private String messageId;
    private String update;
    private Long ttl;

    @DynamoDBHashKey(attributeName="MessageId")
    public String getMessageId() {
        return messageId;
    }
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @DynamoDBAttribute(attributeName="Update")
    public String getUpdate() {
        return update;
    }
    public void setUpdate(String update) {
        this.update = update;
    }

    @DynamoDBAttribute(attributeName="ttl")
    public Long getTtl() { return ttl; }
    public void setTtl(long ttlNumber) { this.ttl = ttlNumber; }
}

package com.autodesk.compute.dynamodb;

import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.testng.Assert;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.JOB_ID;
import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.STATUS;

@Category(UnitTests.class)
@Slf4j
public class TestDynamoDBClient {
    @Test
    public void testGetConditionCheck() {
        String updateExpression = "set #st = :st";

        final NameMap nameMap = new NameMap()
                .with("#st", STATUS);

        final ValueMap valueMap = new ValueMap()
                .withString(":st", JobStatus.INPROGRESS.toString())
                .withString(":stpro", JobStatus.INPROGRESS.toString())
                .withString(":stcom", JobStatus.COMPLETED.toString())
                .withString(":stcan", JobStatus.CANCELED.toString());

        final String conditionExpression = makeString(String.format("attribute_exists(%s)",JOB_ID),
                " AND (:st = :stpro OR NOT #st in (:stcom, :stcan, :st))");

        final String expectedConditionExpression = makeString(String.format("attribute_exists(%s)",JOB_ID),
                " AND (INPROGRESS = INPROGRESS OR NOT Status in (COMPLETED, CANCELED, INPROGRESS))");

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, "job-id")
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap)
                .withConditionExpression(conditionExpression);

        String conditionalCheck = DynamoDBClient.getConditionCheck(updateItemSpec);
        Assert.assertEquals(expectedConditionExpression, conditionalCheck);
    }

    @Test
    public void testGetConditionCheckBadExpression() {
        String updateExpression = "set #st = :st";

        final NameMap nameMap = new NameMap()
                .with("#st", STATUS);

        final ValueMap valueMap = new ValueMap()
                .withString(":st", JobStatus.INPROGRESS.toString())
                .withString(":stpro", JobStatus.INPROGRESS.toString())
                .withString(":stcom", JobStatus.COMPLETED.toString())
                .withString(":stcan", JobStatus.CANCELED.toString());

        final String conditionExpression = makeString(String.format("attribute_exists(%s)",JOB_ID),
                " AND (:st = :stpro OR NOT #st in (:stcom, :stcan, :s");

        final String expectedConditionExpression = makeString(String.format("attribute_exists(%s)",JOB_ID),
                " AND (INPROGRESS = INPROGRESS OR NOT Status in (COMPLETED, CANCELED, <MISSING_VALUE>");

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, "job-id")
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap)
                .withConditionExpression(conditionExpression);

        String conditionalCheck = DynamoDBClient.getConditionCheck(updateItemSpec);
        Assert.assertEquals(expectedConditionExpression, conditionalCheck);
    }

}

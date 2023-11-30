package com.autodesk.compute.validation;

import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.regex.Pattern;

@Category(UnitTests.class)
@Slf4j
public class TestRegex {

    private static final Pattern jobIdPattern = JobDBRecord.JOB_ID_PATTERN;

    // Array job ID is just <guid>:array without any index
    private static final Pattern arrayJobIdPattern = JobDBRecord.ARRAY_JOB_ID_PATTERN;

    @Test
    public void TestJobIdPattern() {
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b").matches());
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array").matches());
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:0").matches());
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:1").matches());
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:2").matches());
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:100").matches());
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:999").matches());
        Assert.assertTrue(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:9999").matches());

        Assert.assertFalse(jobIdPattern.matcher("abcd").matches());
        Assert.assertFalse(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:0").matches());
        Assert.assertFalse(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:099").matches());
        Assert.assertFalse(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:10000").matches());
        Assert.assertFalse(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:100").matches());
        Assert.assertFalse(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:").matches());
        Assert.assertFalse(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:999").matches());
        Assert.assertFalse(jobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:").matches());
    }


    @Test
    public void TestArrayJobIdPattern() {
        Assert.assertTrue(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array").matches());

        Assert.assertFalse(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c").matches());
        Assert.assertFalse(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:0").matches());
        Assert.assertFalse(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:10000").matches());
        Assert.assertFalse(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:100").matches());
        Assert.assertFalse(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:array:").matches());
        Assert.assertFalse(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:999").matches());
        Assert.assertFalse(arrayJobIdPattern.matcher("b0b9a455-ef0f-4e14-a47c-d2a2b5aa6a9b:").matches());
    }

}

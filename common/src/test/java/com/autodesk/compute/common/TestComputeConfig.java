package com.autodesk.compute.common;

import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(UnitTests.class)
@Slf4j
public class TestComputeConfig {

    private ComputeConfig computeConfig;

    @Before
    public void Before () {
        computeConfig = ComputeConfig.getInstance();
    }

    @Test
    public void TestIsCreateJobsAsynchronouslyOK() {
        // Values are validated against this setting in conf.conf
        // job.create.asynchronousExclusions="^(excluded).*, exapp-c-uw2, moniker-p-uw2"
        // All the jobs that does match exclusion list are not allowed to create jobs asynchronously
        Assert.assertFalse(computeConfig.createJobsAsynchronously("excluded-c-uw2"));
        Assert.assertFalse(computeConfig.createJobsAsynchronously("EXCLUDED-c-uw2"));
        Assert.assertFalse(computeConfig.createJobsAsynchronously("Excluded-C-UW2"));
        Assert.assertFalse(computeConfig.createJobsAsynchronously("excluded-sb-c-uw2"));
        Assert.assertFalse(computeConfig.createJobsAsynchronously("excluded_moniker1"));
        Assert.assertFalse(computeConfig.createJobsAsynchronously("exapp-c-uw2"));
        Assert.assertFalse(computeConfig.createJobsAsynchronously("moniker-p-uw2"));

        // All the jobs that does not match exclusion list are allowed to create jobs asynchronously
        Assert.assertTrue(computeConfig.createJobsAsynchronously("abc-excluded-c-uw2"));
        Assert.assertTrue(computeConfig.createJobsAsynchronously("exapp-c-uw2-db"));
        Assert.assertTrue(computeConfig.createJobsAsynchronously("exapp-s-uw2"));
        Assert.assertTrue(computeConfig.createJobsAsynchronously("moniker-s-uw2"));
        Assert.assertTrue(computeConfig.createJobsAsynchronously("moniker-p-uw2-db"));
        Assert.assertTrue(computeConfig.createJobsAsynchronously("included"));
    }
}

package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.ActivityListItem;
import com.amazonaws.services.stepfunctions.model.ListActivitiesRequest;
import com.amazonaws.services.stepfunctions.model.ListActivitiesResult;
import com.autodesk.compute.common.ComputeAWSStepFunctionsDriver;
import com.autodesk.compute.test.TestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class TestCacheActivities {

    @Mock
    AWSStepFunctions sfn;

    @Spy
    @InjectMocks
    ComputeAWSStepFunctionsDriver driver;

    CacheActivities cache;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
        cache = new CacheActivities(driver);
        when(driver.listActivities()).thenCallRealMethod();
    }


    @Test
    public void noActivities() {
        final Answer<ListActivitiesResult> answers = TestHelper.makeAnswer(
                new ListActivitiesResult().withActivities(Collections.emptyList()));
        doAnswer(answers).when(sfn).listActivities(any(ListActivitiesRequest.class));
        final Iterable<String> arns = Collections.singletonList("one-arn");
        final boolean result = cache.areActivitiesCreated(arns);
        Assert.assertFalse(result);
    }

    @Test
    public void secondPageActivityMatches() {
        final Answer<ListActivitiesResult> answers = TestHelper.makeAnswer(
                new ListActivitiesResult().withNextToken("a-token").withActivities(
                        List.of(new ActivityListItem().withActivityArn("zero-arn"))),
                new ListActivitiesResult().withActivities(
                        List.of(new ActivityListItem().withActivityArn("one-arn"))
                ));
        doAnswer(answers).when(sfn).listActivities(any(ListActivitiesRequest.class));
        final Iterable<String> arns = Collections.singletonList("one-arn");
        final boolean result = cache.areActivitiesCreated(arns);
        Assert.assertTrue(result);
    }

    @Test
    public void notAllActivitiesMatch() {
        final Answer<ListActivitiesResult> answers = TestHelper.makeAnswer(
                new ListActivitiesResult().withNextToken("a-token").withActivities(
                        List.of(new ActivityListItem().withActivityArn("zero-arn"))),
                new ListActivitiesResult().withActivities(
                        List.of(new ActivityListItem().withActivityArn("one-arn"))
                ));
        doAnswer(answers).when(sfn).listActivities(any(ListActivitiesRequest.class));
        final Iterable<String> arns = List.of("one-arn", "three-arn");
        final boolean result = cache.areActivitiesCreated(arns);
        Assert.assertFalse(result);
    }

    @Test
    public void allActivitiesMatch() {
        final Answer<ListActivitiesResult> answers = TestHelper.makeAnswer(
                new ListActivitiesResult().withNextToken("a-token").withActivities(
                        List.of(new ActivityListItem().withActivityArn("zero-arn"))),
                new ListActivitiesResult().withActivities(
                        List.of(new ActivityListItem().withActivityArn("one-arn"))
                ));
        doAnswer(answers).when(sfn).listActivities(any(ListActivitiesRequest.class));
        final Iterable<String> arns = List.of("one-arn", "zero-arn");
        final boolean result = cache.areActivitiesCreated(arns);
        Assert.assertTrue(result);
    }

}

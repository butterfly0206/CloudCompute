package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.ListStateMachinesRequest;
import com.amazonaws.services.stepfunctions.model.ListStateMachinesResult;
import com.amazonaws.services.stepfunctions.model.StateMachineListItem;
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

public class TestCacheStateMachines {

    @Mock
    AWSStepFunctions sfn;

    @Spy
    @InjectMocks
    ComputeAWSStepFunctionsDriver driver;

    CacheStateMachines cache;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
        cache = new CacheStateMachines(driver);
        when(driver.listActivities()).thenCallRealMethod();
    }


    @Test
    public void noStateMachines() {
        final Answer<ListStateMachinesResult> answers = TestHelper.makeAnswer(
                new ListStateMachinesResult().withStateMachines(Collections.emptyList()));
        doAnswer(answers).when(sfn).listStateMachines(any(ListStateMachinesRequest.class));
        final Iterable<String> arns = Collections.singletonList("one-arn");
        final boolean result = cache.areStateMachinesCreated(arns);
        Assert.assertFalse(result);
    }

    @Test
    public void secondPageStateMachineMatches() {
        final Answer<ListStateMachinesResult> answers = TestHelper.makeAnswer(
                new ListStateMachinesResult().withNextToken("a-token").withStateMachines(
                        List.of(new StateMachineListItem().withStateMachineArn("zero-arn"))),
                new ListStateMachinesResult().withStateMachines(
                        List.of(new StateMachineListItem().withStateMachineArn("one-arn"))
                ));
        doAnswer(answers).when(sfn).listStateMachines(any(ListStateMachinesRequest.class));
        final Iterable<String> arns = Collections.singletonList("one-arn");
        final boolean result = cache.areStateMachinesCreated(arns);
        Assert.assertTrue(result);
    }

    @Test
    public void notAllStateMachinesMatch() {
        final Answer<ListStateMachinesResult> answers = TestHelper.makeAnswer(
                new ListStateMachinesResult().withNextToken("a-token").withStateMachines(
                        List.of(new StateMachineListItem().withStateMachineArn("zero-arn"))),
                new ListStateMachinesResult().withStateMachines(
                        List.of(new StateMachineListItem().withStateMachineArn("one-arn"))
                ));
        doAnswer(answers).when(sfn).listStateMachines(any(ListStateMachinesRequest.class));
        final Iterable<String> arns = List.of("one-arn", "three-arn");
        final boolean result = cache.areStateMachinesCreated(arns);
        Assert.assertFalse(result);
    }

    @Test
    public void allStateMachinesMatch() {
        final Answer<ListStateMachinesResult> answers = TestHelper.makeAnswer(
                new ListStateMachinesResult().withNextToken("a-token").withStateMachines(
                        List.of(new StateMachineListItem().withStateMachineArn("zero-arn"))),
                new ListStateMachinesResult().withStateMachines(
                        List.of(new StateMachineListItem().withStateMachineArn("one-arn"))
                ));
        doAnswer(answers).when(sfn).listStateMachines(any(ListStateMachinesRequest.class));
        final Iterable<String> arns = List.of("one-arn", "zero-arn");
        final boolean result = cache.areStateMachinesCreated(arns);
        Assert.assertTrue(result);
    }

}

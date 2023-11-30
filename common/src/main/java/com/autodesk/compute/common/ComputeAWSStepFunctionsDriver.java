package com.autodesk.compute.common;


import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.ListActivitiesRequest;
import com.amazonaws.services.stepfunctions.model.ListActivitiesResult;
import com.amazonaws.services.stepfunctions.model.ListStateMachinesRequest;
import com.amazonaws.services.stepfunctions.model.ListStateMachinesResult;

import jakarta.inject.Inject;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

public class ComputeAWSStepFunctionsDriver {
    private static final int SOCKET_TIMEOUT = Math.toIntExact(TimeUnit.SECONDS.toMillis(65));

    private final AWSStepFunctions stepFunctions;

    @Inject
    public ComputeAWSStepFunctionsDriver() {
        // Compute config will probably be needed here eventually
        this(AWSStepFunctionsClientBuilder.standard()
                .withClientConfiguration(
                        PredefinedClientConfigurations.defaultConfig()
                                .withSocketTimeout(SOCKET_TIMEOUT)
                ).build());
    }

    public ComputeAWSStepFunctionsDriver(final AWSStepFunctions stepFunctions) {
        this.stepFunctions = stepFunctions;
    }

    public Iterator<ListActivitiesResult> listActivities() {
        return new Iterator<>() {
            ListActivitiesRequest request = new ListActivitiesRequest().withMaxResults(1_000);
            boolean firstTime = true;
            boolean lastPageLoaded; // false
            String nextToken; // null

            @Override
            public ListActivitiesResult next() {
                if (lastPageLoaded)
                    throw new NoSuchElementException("Finished loading all activities");
                if (nextToken != null)
                    request = request.withNextToken(nextToken);
                final ListActivitiesResult result = stepFunctions.listActivities(request);
                firstTime = false;
                nextToken = result.getNextToken();
                if (nextToken == null)
                    lastPageLoaded = true;
                return result;
            }

            @Override
            public boolean hasNext() {
                return firstTime || nextToken != null;
            }
        };
    }


    public Iterator<ListStateMachinesResult> listStateMachines() {
        return new Iterator<>() {
            ListStateMachinesRequest request = new ListStateMachinesRequest().withMaxResults(1_000);
            boolean firstTime = true;
            boolean lastPageLoaded; // false
            String nextToken; // null

            @Override
            public ListStateMachinesResult next() {
                if (lastPageLoaded)
                    throw new NoSuchElementException("Finished loading all state machines");
                if (nextToken != null)
                    request = request.withNextToken(nextToken);
                final ListStateMachinesResult result = stepFunctions.listStateMachines(request);
                firstTime = false;
                nextToken = result.getNextToken();
                if (nextToken == null)
                    lastPageLoaded = true;
                return result;
            }

            @Override
            public boolean hasNext() {
                return firstTime || nextToken != null;
            }
        };
    }

}

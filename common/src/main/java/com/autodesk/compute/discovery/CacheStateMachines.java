package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.model.ListStateMachinesResult;
import com.autodesk.compute.common.ComputeAWSStepFunctionsDriver;

import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Iterator;

public class CacheStateMachines {
    private final HashSet<String> stateMachineArnsLoaded;
    private final Iterator<ListStateMachinesResult> stateMachinesIter;

    @Inject
    public CacheStateMachines() {
        this(new ComputeAWSStepFunctionsDriver());
    }

    public CacheStateMachines(final ComputeAWSStepFunctionsDriver driver) {
        stateMachinesIter = driver.listStateMachines();
        stateMachineArnsLoaded = new HashSet<>();
    }

    public boolean areStateMachinesCreated(final Iterable<String> stateMachinesArns) {
        for (final String activityArn : stateMachinesArns) {
            if (stateMachineArnsLoaded.contains(activityArn))
                continue;   // Check the next item on the list
            if (!stateMachinesIter.hasNext())    // No more to load?
                return false;   // It's not there
            // Now load making AWS API calls until we find it.
            boolean isPresent;
            do {
                // Add the next batch of activities to the cache
                // The nice part about listActivities is its max page size is 1000.
                stateMachinesIter.next().getStateMachines()
                        .forEach(item -> stateMachineArnsLoaded.add(item.getStateMachineArn()));
                // Track the number of API calls
                isPresent = stateMachineArnsLoaded.contains(activityArn);
                // Still not there, and no more pages?
                if (!isPresent && !stateMachinesIter.hasNext())
                    return false;
            } while (!isPresent);
            // Got to here means we found this one; proceed to the next.
        }
        // Got here means we found everything.
        return true;
    }

}

package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.model.ListActivitiesResult;
import com.autodesk.compute.common.ComputeAWSStepFunctionsDriver;

import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Iterator;

public class CacheActivities {
    private final HashSet<String> activityArnsLoaded;
    private final Iterator<ListActivitiesResult> activitiesIter;

    @Inject
    public CacheActivities() {
        this(new ComputeAWSStepFunctionsDriver());
    }

    public CacheActivities(final ComputeAWSStepFunctionsDriver driver) {
        activitiesIter = driver.listActivities();
        activityArnsLoaded = new HashSet<>();
    }

    public boolean areActivitiesCreated(final Iterable<String> activitiesArns) {
        for (final String activityArn : activitiesArns) {
            if (activityArnsLoaded.contains(activityArn))
                continue;   // Check the next item on the list
            if (!activitiesIter.hasNext())    // No more to load?
                return false;   // It's not there
            // Now load making AWS API calls until we find it.
            boolean isPresent;
            do {
                // Add the next batch of activities to the cache
                // The nice part about listActivities is its max page size is 1000.
                activitiesIter.next().getActivities()
                        .forEach(item -> activityArnsLoaded.add(item.getActivityArn()));
                isPresent = activityArnsLoaded.contains(activityArn);
                // Still not there, and no more pages?
                if (!isPresent && !activitiesIter.hasNext())
                    return false;
            } while (!isPresent);
            // Got to here means we found this one; proceed to the next.
        }
        // Got here means we found everything.
        return true;
    }

}

package com.autodesk.compute.cosv2;

import com.autodesk.compute.model.cosv2.AppState;
import lombok.AllArgsConstructor;

import java.util.HashMap;

@AllArgsConstructor
public class AppStateDriverImpl implements AppStateDriver {

    private final DynamoDBDriver<AppState> dynamoDBDriver;

    private AppState getEmptyAppState(final String appName) {
        return AppState.builder(appName)
                .deploymentState(new HashMap<>())
                .build();
    }

    @Override
    public AppState load(final String appName) {
        return dynamoDBDriver.load(appName, getEmptyAppState(appName));
    }

}

package com.autodesk.compute.testlambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;

public class TestLambdaHandler implements RequestHandler<Map<String, Object>, String> {

    // cosv2 uses this
    public void handle(final Context context) {
        new TestLambdaProcessor().process();
    }

    // Added this to provide health check which is needed by cosv3 lambda pipeline to succeed.
    @Override
    public String handleRequest(Map<String, Object> event, Context context) { //NOPMD
        // Needed for health check needed by cosv3 lambda pipeline
        if (event != null && event.containsKey("Healthcheck")) {
            return "HealthcheckPassed!";
        } else {
            new TestLambdaProcessor().process();
            return "SUCCESS";
        }
    }

    public static void main(final String[] args) {
        new TestLambdaHandler().handle(null);
    }

}

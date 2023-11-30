package com.autodesk.compute.model.cosv2;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import lombok.*;

import java.util.Map;

/**
 * @author Nusli Vakil (vakiln)
 * <p>
 * This class represents the schema for app state stored in dynamodb
 * <p>
 * Make sure a single attribute in the class does not grow too large. A modification to an attribute requires
 * a complete download and upload of that particular attribute.
 * <p>
 * NOTE: Here we do not make our class immutable and depend on setters because of dynamoDB mapper
 */
@DynamoDBTable(tableName = "REPLACED_AT_RUNTIME")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@With
@EqualsAndHashCode
public class AppState {
    // The application Name. This is the primary key
    @DynamoDBHashKey(attributeName = "appName")
    private String appName;

    // The portfolio version which the application DNS currently points to
    @DynamoDBAttribute
    private String deployedPortfolioVersion;

    @DynamoDBAttribute
    private boolean multiVersion;

    @DynamoDBAttribute
    private String obsS3BucketName;

    @DynamoDBAttribute
    private String obsS3BucketPrefix;

    /**
     * Holds the state for each deployment
     * Structure: timestamp: {
     * portfolioVersion: portfolioVersion of the app
     * state: output
     * }
     */
    @DynamoDBAttribute
    private Map<String, Map<String, String>> deploymentState;

    /**
     * Holds the state for each deployed version
     * Structure:
     * portfolioVersion: {
     * timeStamp: UTC
     * state:
     * }
     */
    @DynamoDBAttribute
    private Map<String, Map<String, String>> versionsCatalog;

    @DynamoDBVersionAttribute
    private Long version;

    // By making the empty builder private we can expose a public version which takes in the MUST HAVE fields
    private static AppState.AppStateBuilder builder() {
        return new AppState.AppStateBuilder();
    }

    public static AppState.AppStateBuilder builder(final String appName) {
        return builder().appName(appName);
    }
}
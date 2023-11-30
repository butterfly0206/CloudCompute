package com.autodesk.compute.dynamodb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException;
import com.amazonaws.services.dynamodbv2.model.TransactionConflictException;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.amazonaws.util.ValidationUtils.assertNotNull;
import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.ComputeUtils.firstSupplier;

@Slf4j
public class DynamoDBClient {
    private static final ClientConfiguration clientConfiguration = PredefinedClientConfigurations.dynamoDefault();

    protected final AmazonDynamoDB amazonDynamoDB;
    protected final DynamoDB dynamoDB;

    static {
        final RetryPolicy.BackoffStrategy backoff = new PredefinedBackoffStrategies.SDKDefaultBackoffStrategy(25,
                500,
                2000);

        final List<Class<? extends Exception>> exceptionsToRetryOn = Arrays.asList(TransactionConflictException.class);
        final RetryOnExceptionsCondition retryOnExceptionsCondition = new RetryOnExceptionsCondition(exceptionsToRetryOn);

        final RetryPolicy retry = new RetryPolicy(retryOnExceptionsCondition, backoff, 2, false);

        clientConfiguration.setConnectionTimeout(8_000);           // 8 seconds
        clientConfiguration.setSocketTimeout(8_000);              // 8 seconds
        clientConfiguration.setClientExecutionTimeout(9_000);     // 9 seconds
        clientConfiguration.setRetryPolicy(retry);
        clientConfiguration.setMaxErrorRetry(2);
        //The default value for maximum connections is 50. Changing that to be 100 to see if this is the underlying cause of ClientExecutionTimeouts
        clientConfiguration.setMaxConnections(100);
        clientConfiguration.setRequestTimeout(5_000);
    }

    protected DynamoDBClient() {
        this(null);
    }

    protected DynamoDBClient(final AmazonDynamoDB dynamoDBClientTemp) {

        DynamoDB tempDynamoDB = null;           //NOPMD - needed to avoid 'maybe not initialized' warnings below
        AmazonDynamoDB tempClientToUse = null;  //NOPMD

        try {
            tempClientToUse = firstSupplier(
                    () -> dynamoDBClientTemp,
                    () -> {
                        final AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard();
                        builder.setClientConfiguration(clientConfiguration);
                        return builder.build();
                    } );
            assert tempClientToUse != null;
            tempDynamoDB = new DynamoDB(tempClientToUse);
        } catch (final Exception e) {
            log.error("DynamoDBClient failed with error: ", e);
            System.exit(1);
        }

        amazonDynamoDB = tempClientToUse;
        dynamoDB = tempDynamoDB;
    }

    protected IDynamoDBJobClientConfig getComputeConfig() {
        return ComputeConfig.getInstance();
    }

    public static ClientConfiguration getClientConfiguration() {
        return clientConfiguration;
    }

    // Some of the source derived from https://github.com/aws/aws-sdk-java/blob/7b1e5b87b0bf03456df9e77716b14731adf9a7a7/aws-java-sdk-core/src/main/java/com/amazonaws/retry/v2/RetryOnExceptionsCondition.java
    private static class RetryOnExceptionsCondition extends PredefinedRetryPolicies.SDKDefaultRetryCondition {
        private final List<Class<? extends Exception>> exceptionsToRetryOn;

        public RetryOnExceptionsCondition(final List<Class<? extends Exception>> exceptionsToRetryOn) {
            this.exceptionsToRetryOn = new ArrayList<>(
                    assertNotNull(exceptionsToRetryOn, "exceptionsToRetryOn"));
        }

        @Override
        public boolean shouldRetry(final AmazonWebServiceRequest originalRequest,
                                   final AmazonClientException exception,
                                   final int retriesAttempted) {
            // Checks default retry condition first including isThrottlingException
            // https:
//github.com/aws/aws-sdk-java/blob/ff15b1c09e9d4cd23632abbe28e8b86f6552f673/aws-java-sdk-core/src/main/java/com/amazonaws/retry/PredefinedRetryPolicies.java#L166
            if (super.shouldRetry(originalRequest, exception, retriesAttempted)) {
                return true;
            }
            // Retry on specific instance of TransactionCanceledException where one of the
            // causes is TransactionConflict
            if (exceptionMatches(exception, TransactionCanceledException.class)) {
                final TransactionCanceledException tcx = (TransactionCanceledException) exception;
                if (tcx.getCancellationReasons() != null &&
                        tcx.getCancellationReasons().stream().anyMatch(r -> r.getCode().equals("TransactionConflict"))) {
                    return true;
                }

            }
            return shouldRetry(exception);
        }

        public boolean shouldRetry(final Exception exception) {
            if (exception != null) {
                for (final Class<? extends Exception> exceptionClass : exceptionsToRetryOn) {
                    if (exceptionMatches(exception, exceptionClass)) {
                        return true;
                    }
                    // Note that we check the wrapped exception too because for things like SocketException or IOException
                    // we wrap them in an SdkClientException before throwing.
                    if (wrappedCauseMatches(exception, exceptionClass)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean exceptionMatches(final Exception exception, final Class<? extends Exception> exceptionClass) {
            return exception.getClass().equals(exceptionClass);
        }

        private boolean wrappedCauseMatches(final Exception exception, final Class<? extends Exception> exceptionClass) {
            if (exception.getCause() == null) {
                return false;
            }
            return exception.getCause().getClass().equals(exceptionClass);
        }
    }

    protected DynamoDBMapper buildDynamoDBMapper(final String dynamoDbTable, final DynamoDBMapperConfig.ConsistentReads readsOption) {
        final DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withTableNameOverride(DynamoDBMapperConfig
                        .TableNameOverride
                        .withTableNameReplacement(dynamoDbTable)
                )
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)
                .withConsistentReads(readsOption)
                .build();
        return new DynamoDBMapper(amazonDynamoDB, mapperConfig);
    }
    protected DynamoDBMapper buildDynamoDBMapper(final String dynamoDbTable) {
        return buildDynamoDBMapper(dynamoDbTable, DynamoDBMapperConfig.ConsistentReads.EVENTUAL);
    }

    protected DynamoDBMapper buildDynamoDBMapper(final DynamoDBMapperConfig.DefaultTableNameResolver tableNameResolver) {
        final DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withTableNameResolver(tableNameResolver)
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)
                .build();
        return new DynamoDBMapper(amazonDynamoDB, mapperConfig);
    }

    // public for mocking
    public UpdateItemOutcome updateWithConditionCheckDescription(final Table table, final UpdateItemSpec updateItemSpec, final String conditionDescription) throws ComputeException {
        try {
            if (table == null) {
                throw new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, "table is null");
            } else if (updateItemSpec == null) {
                throw new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, "updateItemSpec is null");
            }
            return table.updateItem(updateItemSpec);
        } catch (final ComputeException e) {
            // this is us from above, just rethrow it
            throw e;
        } catch (final ConditionalCheckFailedException e) {
            String conditionCheck = getConditionCheck(updateItemSpec);
            String errorMessage = makeString("ConditionCheckDescription: ", conditionDescription, "; ConditionCheck: ", conditionCheck, "; Exception:")
                    + e.getErrorMessage();
            e.setErrorMessage(errorMessage);
            log.warn("Update of table {} failed because condition ({}) was not met", table.getTableName(), conditionCheck);
            throw e;
        } catch (final Exception e) {
            throw new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
    }

    public static String getConditionCheck(final UpdateItemSpec updateItemSpec) {
        StringBuilder sb = new StringBuilder();
        String expression = updateItemSpec.getConditionExpression();
        for (int i = 0; i < expression.length(); i++) {
            // Check if it's start of name (starts with #) or value (starts with :) token
            if(expression.charAt(i) == '#' || expression.charAt(i) == ':') {
                int start = i;
                while (i+1 < expression.length() && Character.isAlphabetic(expression.charAt(i+1))) {
                    i++;
                }
                String token = expression.substring(start, i+1);
                if(expression.charAt(start) == '#') {
                    sb.append(updateItemSpec.getNameMap().getOrDefault(token, "<MISSING_NAME>"));
                } else {
                    sb.append(updateItemSpec.getValueMap().getOrDefault(token, "<MISSING_VALUE>").toString());
                }
            } else {
                sb.append(expression.charAt(i));
            }
        }
        return sb.toString();
    }

}

package com.autodesk.compute.dynamodb;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Creates a local DynamoDB instance for testing.
 */
public class LocalDynamoDBCreationRule extends ExternalResource {

    private DynamoDBProxyServer server;
    private AmazonDynamoDB amazonDynamoDB;
    private static final ClientConfiguration clientConfiguration = PredefinedClientConfigurations.dynamoDefault();
    static {
        RetryPolicy.BackoffStrategy backoff = new PredefinedBackoffStrategies.SDKDefaultBackoffStrategy(25,
                500,
                2000);

        RetryPolicy retry = new RetryPolicy(null, backoff, 5, false);

        clientConfiguration.setConnectionTimeout(30000);         // 30 secondsmilliseconds
        clientConfiguration.setSocketTimeout(50000);             // 50 seconds
        clientConfiguration.setRequestTimeout(5000);             // 5 seconds
        clientConfiguration.setClientExecutionTimeout(30000);
        clientConfiguration.setRetryPolicy(retry);
    }

    public LocalDynamoDBCreationRule() {
        // This one should be copied during test-compile time. If project's basedir does not contains a folder
        // named 'native-libs' please try '$ mvn clean install' from command line first
        System.setProperty("sqlite4java.library.path", "native-libs");
    }

    @Override
    protected void before() throws RuntimeException {
        try {
            final String port = getAvailablePort();
            this.server = ServerRunner.createServerFromCommandLineArgs(new String[]{"-inMemory", "-port", port});
            server.start();
            AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard()
                    .withClientConfiguration(clientConfiguration)
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:" + port, "us-west-2"))
                    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("access", "secret")));
            amazonDynamoDB = builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Exception in LocalDynamoDBCreationRule::before = " + e.toString());
        }
    }

    @Override
    protected void after() {

        if (server == null) {
            return;
        }

        try {
            server.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AmazonDynamoDB getAmazonDynamoDB() {
        return amazonDynamoDB;
    }

    private String getAvailablePort() {
        try (final ServerSocket serverSocket = new ServerSocket(0)) {
            return String.valueOf(serverSocket.getLocalPort());
        } catch (IOException e) {
            throw new RuntimeException("Available port was not found", e);
        }
    }
}
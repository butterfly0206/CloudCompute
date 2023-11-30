package com.autodesk.compute.cosv2;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import lombok.extern.slf4j.Slf4j;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;

/**
 * @author Nusli Vakil (vakiln)
 * <p>
 * A driver for interfacing with DynamoDB
 * <p>
 * For read-modify-write operatoins, it is best to use the DynamoDBVersionAttribute in your Item Class and then use the
 * transform method in order to limit contention with other processes/threads.
 * <p>
 * If you do use load and save for such operations you will have to take care of handling the ConditionalCheckFailedException
 * at the time of saving the data.
 */
@Slf4j
public class DynamoDBDriverImpl<I> implements DynamoDBDriver<I> {
    private final DynamoDBMapperConfig config;
    private final DynamoDBMapper mapper;
    private final Class<I> itemClass;

    /**
     * Constructor with Custom credentials
     *
     * @param tableName      Name of the table
     * @param region         AWS region
     * @param awsCredentials AWS credentials
     */
    public DynamoDBDriverImpl(final Class<I> itemClass, final String tableName, final String region, final AWSCredentials awsCredentials) {
        this.itemClass = itemClass;
        config = DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(tableName).config();
        final AmazonDynamoDB client = makeStandardClient(AmazonDynamoDBClientBuilder.standard(), AmazonDynamoDB.class, awsCredentials, region);
        mapper = new DynamoDBMapper(client);
    }

    /**
     * Constructor using the default AWS credentials
     *
     * @param tableName Name of the table
     */
    public DynamoDBDriverImpl(final Class<I> itemClass, final String tableName) {
        this(itemClass, tableName, null, null);
    }

    @Override
    public I load(final Object key, final I defaultItem) {
        final I item = mapper.load(itemClass, key, config);
        if (item == null) {
            return defaultItem;
        } else {
            return item;
        }
    }

}
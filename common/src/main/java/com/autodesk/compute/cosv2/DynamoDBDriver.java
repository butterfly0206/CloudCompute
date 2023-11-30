package com.autodesk.compute.cosv2;

/**
 * @author Nusli Vakil (vakiln)
 * <p>
 * A interface for interacting with DynamoDB
 * <p>
 * For read-modify-write operatoins, it is best to use the DynamoDBVersionAttribute in your Item Class and then use the
 * transform method in order to limit contention with other processes/threads.
 * <p>
 * If you do use load and save for such operations you will have to take care of handling the ConditionalCheckFailedException
 * at the time of saving the data.
 */
public interface DynamoDBDriver<I> {
    /**
     * Load the item from the DB
     *
     * @param key         primary key
     * @param defaultItem if item is not present in the DB, return this item instead.
     * @return Dynamo DB item
     */
    I load(Object key, I defaultItem);
}

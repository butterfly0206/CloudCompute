package com.autodesk.compute.model.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel.DynamoDBAttributeType;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.autodesk.compute.configuration.ComputeConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;

/**
 * Info for the queuing
 */
@JsonInclude(Include.NON_NULL)
@DynamoDBDocument
public class QueueInfo {

	public static final int VISIBILITY_TIMEOUT_IN_SECONDS = 30;

	@JsonInclude(Include.NON_NULL)
	@DynamoDBDocument
	public enum StatusEnum {
		NONE,
		QUEUED,
		PROCESSING,
		REMOVED,
	}

	@JsonProperty(ComputeConstants.DynamoFields.STATUS)    //NOPMD
	private StatusEnum status = StatusEnum.QUEUED;

	@JsonProperty(VERSION)
	private int version = 1;

	@JsonProperty(QUEUED)
	private boolean inQueue = true;

	@JsonProperty(SELECTED_FROM_QUEUE)
	private boolean selected;

	@JsonProperty(ADD_TO_QUEUE_TIMESTAMP)
	private Long addTimestamp;

	@JsonProperty(LAST_UPDATED_TIMESTAMP)
	private Long updateTimestamp;

	@JsonProperty(PEEK_FROM_QUEUE_TIMESTAMP)
	private Long peekTimestamp;

	@JsonProperty(REMOVE_FROM_QUEUE_TIMESTAMP)
	private Long removeTimestamp;

	public QueueInfo() {
		this.updateTimestamp = System.currentTimeMillis();
	}

	@DynamoDBAttribute(attributeName = STATUS)
	@DynamoDBTyped(DynamoDBAttributeType.S)
	public StatusEnum getStatus() {
		return status;
	}

	public void setStatus(final StatusEnum status) {
		this.status = status;
	}

	public void setStatus(final String statusStr) {
		this.status = StatusEnum.valueOf(statusStr.toUpperCase());
	}

	@DynamoDBAttribute(attributeName = VERSION)
	@DynamoDBVersionAttribute
	public int getVersion() {
		return version;
	}

	public void setVersion(final int version) {
		this.version = version;
	}
	@DynamoDBAttribute(attributeName = QUEUED)
	public boolean isInQueue() {
		return inQueue;
	}

	public void setInQueue(final boolean inQueue) {
		this.inQueue = inQueue;
	}	

	@DynamoDBAttribute(attributeName = SELECTED_FROM_QUEUE)
	@DynamoDBTyped(DynamoDBAttributeType.BOOL)
	public boolean isSelected() {
		return selected;
	}

	public void setSelected(final boolean selected) {
		this.selected = selected;
	}

	@DynamoDBAttribute(attributeName = ADD_TO_QUEUE_TIMESTAMP)
	public Long getAddTimestamp() {
		return addTimestamp;
	}

	public void setAddTimestamp(final Long addTimestamp) {
		this.addTimestamp = addTimestamp;
	}

	@DynamoDBAttribute(attributeName = LAST_UPDATED_TIMESTAMP)
	public Long getUpdateTimestamp() {
		return updateTimestamp;
	}

	public void setUpdateTimestamp(final Long updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	@DynamoDBAttribute(attributeName = PEEK_FROM_QUEUE_TIMESTAMP)
	public Long getPeekTimestamp() {
		return peekTimestamp;
	}

	public void setPeekTimestamp(final Long peekTimestamp) {
		this.peekTimestamp = peekTimestamp;
	}

	@DynamoDBAttribute(attributeName = REMOVE_FROM_QUEUE_TIMESTAMP)
	public Long getRemoveTimestamp() {
		return removeTimestamp;
	}

	public void setRemoveTimestamp(final Long removeTimestamp) {
		this.removeTimestamp = removeTimestamp;
	}
}
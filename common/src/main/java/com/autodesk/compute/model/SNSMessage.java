package com.autodesk.compute.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = SNSMessage.SNSMessageBuilder.class)
public class SNSMessage {

    @With
    @JsonProperty("Type")
    public String type;

    @With
    @JsonProperty("MessageId")
    public String messageId;

    @With
    @JsonProperty("Token")
    public String token;

    @With
    @JsonProperty("TopicArn")
    public String topicArn;

    @With
    @JsonProperty("Subject")
    public String subject;

    @With
    @JsonProperty("Message")
    public String message;

    @With
    @JsonProperty("Timestamp")
    public String timestamp;

    @With
    @JsonProperty("SignatureVersion")
    public String signatureVersion;

    @With
    @JsonProperty("Signature")
    public String signature;

    @With
    @JsonProperty("SigningCertURL")
    public String signingCertURL;

    @With
    @JsonProperty("SubscribeURL")
    public String subscribeURL;

    @With
    @JsonProperty("UnsubscribeURL")
    public String unsubscribeURL;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SNSMessageBuilder{}
}

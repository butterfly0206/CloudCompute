package com.autodesk.compute.common;

//  Copyright (c) 2018 by Autodesk, Inc.
//  All rights reserved.
//
//  The information contained herein is confidential and proprietary to
//  Autodesk, Inc., and considered a trade secret as defined under civil
//  and criminal statutes.  Autodesk shall pursue its civil and criminal
//  remedies in the event of unauthorized use or misappropriation of its
//  trade secrets.  Use of this information by anyone other than authorized
//  employees of Autodesk, Inc. is granted only under a written non-
//  disclosure agreement, expressly prescribing the scope and manner of
//  such use.
//


import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.SdkBaseException;
import com.amazonaws.retry.RetryPolicy.RetryCondition;
import com.amazonaws.retry.RetryUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * This class is loosely based on PredefinedRetryPolicies.SDKDefaultRetryCondition in the AWS SDK
 */
public final class AWSRetryCondition implements RetryCondition {
    private static final int SC_TOO_MANY_REQUESTS = 429;
    // Deal with eventual consistency issues in S3 by treating a 404 as a retry-able status code
    private static final List<Integer> RETRYABLE_STATUS_CODES = Arrays.asList(HttpStatus.SC_NOT_FOUND, SC_TOO_MANY_REQUESTS);

    @Override
    public boolean shouldRetry(final AmazonWebServiceRequest originalRequest, final AmazonClientException exception, final int retriesAttempted) {
        if (exception.getCause() instanceof IOException) {
            return true;
        }
        if (exception instanceof SdkBaseException) {
            final SdkBaseException sdkBaseException = exception;
            if (RetryUtils.isRetryableServiceException(sdkBaseException)
                    || RetryUtils.isThrottlingException(sdkBaseException)
                    || RetryUtils.isClockSkewError(sdkBaseException)) {
                return true;
            }
        }
        if (exception instanceof AmazonServiceException) {
            final AmazonServiceException amazonServiceException = (AmazonServiceException) exception;
            if (StringUtils.isNotEmpty(amazonServiceException.getErrorCode()) && RETRYABLE_STATUS_CODES.contains(amazonServiceException.getStatusCode())) {
                return true;
            }
        }
        return false;
    }
}

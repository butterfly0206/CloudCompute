package com.autodesk.compute.api.impl;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.sns.message.SnsMessage;
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.util.StringInputStream;
import com.autodesk.compute.api.AppUpdateApiService;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
public class AppUpdateApiServiceImpl extends AppUpdateApiService {
    @Inject
    public AppUpdateApiServiceImpl() {
        // Injection empty constructor
    }

    public ComputeConfig getComputeConfig() { return ComputeConfig.getInstance(); }

    @Override
    public Response appUpdateGet(final SecurityContext context, final HttpHeaders headers, final String body)
            throws ComputeException {

        try {
            final SnsMessageManager messageManager = new SnsMessageManager(getComputeConfig().getRegion());
            final SnsMessage message = messageManager.parseMessage(new StringInputStream(body));
            message.handle(new AdfUpdateMessageHandler());
            return Response.status(Response.Status.OK).build();
        } catch (final SdkClientException e) {
            log.error("AppUpdateGet: Error decoding SNS message: ", e);
            throw e;
        } catch (final Exception e) {
            log.error(makeString("AppUpdateGet failed with code: ", e.getMessage()), e);
            throw new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }
}
package com.autodesk.compute.testlambda;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.model.SlackMessage;
import com.autodesk.compute.testlambda.gen.ApiException;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import jakarta.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
public class SlackClient {

    private final Config conf;
    private final ComputeUtils.TimeoutSettings timeoutSettings;

    @Inject
    public SlackClient() {
        this(ComputeUtils.TimeoutSettings.SLOW);
    }

    public SlackClient(final ComputeUtils.TimeoutSettings timeoutSettings) {
        this.timeoutSettings = timeoutSettings;
        this.conf = Config.getInstance();
    }

    public static String quoteMessage(final Object message) {
        return makeString("```", message, "```");
    }

    // This is here for testing/mocking
    public HttpResponse<String> executeRequest(final HttpRequest request) throws IOException {
        return ComputeUtils.executeRequestForJavaNet(request, this.timeoutSettings);
    }

    public boolean goodResponse(final HttpResponse response) {
        return response.statusCode() == HttpStatus.SC_OK;
    }

    public void postToSlack(final String message) {
        try {
            final int readTimeout = (timeoutSettings == ComputeUtils.TimeoutSettings.FAST) ? 2 : 5;
            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(conf.getSlackCriticalWebhookUrl()))
                    .timeout(Duration.of(readTimeout, ChronoUnit.SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(message));

            builder.setHeader("User-Agent", makeString("ComputeTestJobHandler/", conf.getCloudosPortfolioVersion()));
            builder.setHeader("Content-Type", "application/json");

            final HttpResponse<String> response = executeRequest(builder.build());
            if (!goodResponse(response)) {
                final String responseMessage = response.body();
                log.error("Error posting message to slack channel: {}", responseMessage);
            }
        } catch (final IOException e) {
            log.error("Error posting message to slack channel:", e);
        }
    }

    // Split up so it can be mocked
    public void postToSlack(final List<SlackMessage.Attachment> attachments) {
        final String message = makeSlackMessageJson(attachments);
        postToSlack(message);
    }

    public String makeSlackMessageJson(final List<SlackMessage.Attachment> attachments) {
        final SlackMessage slackMessage = SlackMessage.builder()
                .text(makeString("*_", conf.getAppMoniker(), "_* TestJobLambda v", conf.getCloudosPortfolioVersion()))
                .attachments(attachments)
                .mrkdwn(true).build();

        return new Gson().toJson(slackMessage);
    }

    public static SlackMessage.Attachment toAttachment(final String message, final SlackMessage.MessageSeverity severity) {
        final String color = severity.toString();
        return SlackMessage.Attachment.builder()
                .color(color)
                .title(message)
                .build();
    }

    public static SlackMessage.Attachment toAttachment(final String message, final Exception exception) {
        final String messageWithHere = makeHereMessage(message);
        final String stackTrace = makeStackTrace(exception);

        final String text = makeString("*StackTrace*: ", quoteMessage(stackTrace));
        return SlackMessage.Attachment.builder()
                .color(SlackMessage.MessageSeverity.DANGER.toString())
                .title(messageWithHere)
                .text(text).build();
    }

    public static SlackMessage.Attachment toAttachment(final String message, final ApiException exception) {
        final String messageWithHere = makeHereMessage(message);
        final String stackTrace = makeStackTrace(exception);

        final String text = makeString("*Response code*: ", quoteMessage(exception.getCode()),
                "\n*Response*: ", quoteMessage(exception.getResponseBody()),
                "\n*StackTrace*: ", quoteMessage(stackTrace));
        return SlackMessage.Attachment.builder()
                .color(SlackMessage.MessageSeverity.DANGER.toString())
                .title(messageWithHere)
                .text(text).build();
    }

    public static String makeStackTrace(final Exception exception) {
        final StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        return stackTrace.toString();
    }

    public static String makeHereMessage(final String message) {
        return "<!here> " + message;
    }
}

package com.autodesk.compute.common;

import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.pivovarit.function.ThrowingConsumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestMetrics {
    @Mock
    private AmazonCloudWatch cw;

    @Mock
    private PutMetricDataResult putMetricResult;

    @Mock
    private SdkHttpMetadata sdkMetadata;

    @Spy
    @InjectMocks
    private Metrics metrics;

    private MockMetrics mockMetrics;

    private Metrics currentMetrics;

    private static Map<Class<?>, Object> finalArgumentMap = createFinalArgumentMap();

    private static Map<Class<?>, Object> createFinalArgumentMap() {
        Map<Class<?>, Object> finalArgument = Stream.of(1, "string", 1.0).collect(
                Collectors.toMap(Object::getClass, i -> i));
        finalArgument.put(double.class, 1.0);   // Double.class != double.class
        finalArgument.put(int.class, 1);        // Integer.class != int.class
        return finalArgument;
    }

    @Before
    public void initialize() {
        mockMetrics = new MockMetrics();
        MockitoAnnotations.openMocks(this);
        when(putMetricResult.getSdkHttpMetadata()).thenReturn(sdkMetadata);
        when(sdkMetadata.getHttpStatusCode()).thenReturn(200);
        when(cw.putMetricData(any(PutMetricDataRequest.class))).thenReturn(putMetricResult);
    }

    @Test
    public void writeAMetric() throws Exception {
        metrics.reportJobCanceled("moniker", "worker");
        metrics.close();
        verify(cw, times(1)).putMetricData(any(PutMetricDataRequest.class));
    }

    private Object[] assembleArguments(Method method) {
        var args = new ArrayList<>();
        // Methods with more than two arguments take a moniker and a worker.
        if (method.getParameterCount() >= 2) {
            args.add("moniker");
            args.add("worker");
        }
        // Odd numbered arguments means the method has a value of some sort - it's not a tick metric.
        if (method.getParameterCount() % 2 == 1)
            args.add(finalArgumentMap.get(method.getParameterTypes()[method.getParameterCount() - 1]));
        return args.toArray();
    }

    private void callMethod(Method method) throws IllegalAccessException, InvocationTargetException {
        method.invoke(currentMetrics, assembleArguments(method));
    }

    private int divInt(int val, int divisor) {
        return (int)Math.ceil((double)val / divisor);
    }

    @Test
    public void writeAllMetrics() throws Exception {
        ThrowingConsumer<Method, Exception> wrappedCallMethod = this::callMethod;
        var reportMethods = Arrays.stream(Metrics.class.getMethods()).filter(
                method -> method.getName().startsWith("report")).collect(Collectors.toList());

        // Use the mock metrics test to count up the number of metrics that the real metrics test plans to write.
        currentMetrics = mockMetrics;
        reportMethods.forEach(wrappedCallMethod.uncheck());
        currentMetrics.close();
        var writtenMetrics = mockMetrics.getAllCapturedMetrics();
        // Each one will write either one or two metrics, based on whether they have an undimensioned metric.
        for (var oneMetricValues : writtenMetrics.values()) {
            Assert.assertTrue(oneMetricValues.size() == 1 || oneMetricValues.size() == 2);
        }
        int countOfMetricsWritten = writtenMetrics.values().stream()
                .map(Collection::size).reduce(0, Integer::sum);

        currentMetrics = metrics;
        reportMethods.forEach(wrappedCallMethod.uncheck());
        currentMetrics.close();
        // AWS only allows metrics to be published using PutMetricData in batches of at most 20
        // at a time. https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/cloudwatch_limits.html
        // So: we call them in batches of 20 in our code, which means that the number of times we call it
        // is the ceil(number of metrics written / 20) - we call divInt to facilitate the math.
        verify(cw, times(divInt(countOfMetricsWritten, 20)))
                .putMetricData(any(PutMetricDataRequest.class));

    }
}

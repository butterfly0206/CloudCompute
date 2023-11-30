/*
 * CloudOS Compute API
 * CloudOS Compute API for batch style workloads in Autodesk. Developers can register compute worker types by following the cloudOS2.0 onboarding process. Users can submit jobs against registered worker types. The system treats input and output as opaque JSON payloads that must confirm to the JSON schema specified by the worker documentation outside this system. All APIs are used with the /api/v1 prefix.
 *
 * The version of the OpenAPI document: 1.0.19
 * Contact: cloudos-compute@autodesk.com
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.autodesk.compute.testlambda.gen;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.*;

import java.io.IOException;

public class ProgressRequestBody extends RequestBody {

    private final RequestBody requestBody;

    private final ApiCallback callback;

    public ProgressRequestBody(final RequestBody requestBody, final ApiCallback callback) {
        this.requestBody = requestBody;
        this.callback = callback;
    }

    @Override
    public MediaType contentType() {
        return requestBody.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return requestBody.contentLength();
    }

    @Override
    public void writeTo(final BufferedSink sink) throws IOException {
        final BufferedSink bufferedSink = Okio.buffer(sink(sink));
        requestBody.writeTo(bufferedSink);
        bufferedSink.flush();
    }

    private Sink sink(final Sink sink) {
        return new ForwardingSink(sink) {

            long bytesWritten = 0L;
            long contentLength = 0L;

            @Override
            public void write(final Buffer source, final long byteCount) throws IOException {
                super.write(source, byteCount);
                if (contentLength == 0) {
                    contentLength = contentLength();
                }

                bytesWritten += byteCount;
                callback.onUploadProgress(bytesWritten, contentLength, bytesWritten == contentLength);
            }
        };
    }
}

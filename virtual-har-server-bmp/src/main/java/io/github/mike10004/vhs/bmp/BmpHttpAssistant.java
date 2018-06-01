package io.github.mike10004.vhs.bmp;

import com.google.common.net.MediaType;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.stream.Stream;

class BmpHttpAssistant implements HttpAssistant<RequestCapture, HttpResponse> {

    private static final Logger log = LoggerFactory.getLogger(BmpHttpAssistant.class);

    @Override
    public ParsedRequest parseRequest(RequestCapture capture) throws IOException {
        return capture.request;
    }

    @Override
    public HttpResponse transformRespondable(RequestCapture incomingRequest, HttpRespondable respondable) throws IOException {
        return transformRespondable(incomingRequest.httpVersion, respondable);
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    protected int maybeGetLength(HttpRespondable respondable, int defaultValue) {
        return defaultValue; // TODO get length from Content-Length
    }

    @Override
    public HttpResponse constructResponse(RequestCapture incomingRequest, ImmutableHttpResponse httpResponse) {
        byte[] body;
        try {
            body = httpResponse.getDataSource().read();
        }  catch (IOException e) {
            log.warn("failed to reconstitute response", e);
            body = new byte[0];
        }
        return constructResponseFromParts(incomingRequest.httpVersion, HttpResponseStatus.valueOf(httpResponse.status),
                httpResponse.getContentType(), body);
    }

    private HttpResponse transformRespondable(HttpVersion httpVersion,
                                              HttpRespondable respondable) throws IOException {
        HttpResponseStatus status = HttpResponseStatus.valueOf(respondable.getStatus());
        ByteArrayOutputStream baos = new ByteArrayOutputStream(maybeGetLength(respondable, 256));
        byte[] responseData;
        respondable.writeBody(baos);
        responseData = baos.toByteArray();
        return constructResponseFromParts(httpVersion, status, respondable.streamHeaders(), responseData);
    }

    private HttpResponse constructResponseFromParts(HttpVersion httpVersion, HttpResponseStatus status, Stream<? extends Entry<String, String>> headerStream, byte[] responseData) {
        ByteBuf content = Unpooled.wrappedBuffer(responseData);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(httpVersion, status, content);
        HttpHeaders headers = response.headers();
        headerStream.forEach(header -> {
            headers.add(header.getKey(), header.getValue());
        });
        return response;
    }

    private HttpResponse constructResponseFromParts(HttpVersion httpVersion, HttpResponseStatus status, @Nullable MediaType contentType, byte[] responseData) {
        if (contentType == null) {
            contentType = MediaType.OCTET_STREAM;
        }
        ByteBuf content = Unpooled.wrappedBuffer(responseData);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(httpVersion, status, content);
        HttpHeaders headers = response.headers();
        headers.add(com.google.common.net.HttpHeaders.CONTENT_TYPE, contentType.toString());
        headers.add(com.google.common.net.HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        return response;
    }

}

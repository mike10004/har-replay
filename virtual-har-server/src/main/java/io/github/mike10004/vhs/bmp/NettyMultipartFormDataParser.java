package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.ContentDisposition;
import io.github.mike10004.vhs.harbridge.FormDataPart;
import io.github.mike10004.vhs.harbridge.MultipartFormDataParser;
import io.github.mike10004.vhs.harbridge.TypedContent;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Form data parser that uses Netty's implementation under the hood.
 */
public class NettyMultipartFormDataParser implements MultipartFormDataParser {

    private static final Logger log = LoggerFactory.getLogger(NettyMultipartFormDataParser.class);

    public NettyMultipartFormDataParser() {
    }

    protected HttpRequest mockRequest(MediaType contentType, byte[] data) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.POST, "", Unpooled.wrappedBuffer(data));
        request.headers().set(HttpHeaders.CONTENT_TYPE, contentType.toString());
        return request;
    }

    @Override
    public List<FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] content) throws BadMultipartFormDataException, RuntimeIOException {
        HttpDataFactory dataFactory = new DefaultHttpDataFactory(false);
        HttpRequest request = mockRequest(contentType, content);
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(dataFactory, request);
        List<FormDataPart> parts = new ArrayList<>();
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data != null) {
                try {
                    switch (data.getHttpDataType()) {
                        case Attribute:
                            handleAttribute((Attribute) data, parts);
                            break;
                        case FileUpload:
                            handleFileUpload((FileUpload) data, parts);
                            break;
                        case InternalAttribute:
                            handleInternalAttribute(data, parts);
                            break;
                        default:
                            log.info("unhandled HttpDataType: {}", data.getHttpDataType());
                    }
                } catch (IOException e) {
                    throw new RuntimeIOException("netty threw exception", e);
                } finally {
                    data.release();
                }
            }
        }
        log.debug("{} parts parsed from form data", parts.size());
        return parts;
    }

    @SuppressWarnings("unused") // not sure what to do with these, if anything, so the parts list goes unused
    protected void handleInternalAttribute(InterfaceHttpData attr, List<FormDataPart> parts) {
        log.debug("internal attribute of {} encountered: {} name={}", attr.getClass(), attr.getHttpDataType(), attr.getName());
    }

    protected void handleAttribute(Attribute attr, List<FormDataPart> parts) throws IOException {
        parts.add(toFormDataPart(attr, null));
    }

    @Nullable
    protected String maybeGetFilename(HttpData httpData) {
        requireNonNull(httpData, "httpData");
        if (httpData instanceof FileUpload) {
            return ((FileUpload)httpData).getFilename();
        }
        return null;
    }

    protected FormDataPart toFormDataPart(HttpData httpData, @Nullable String partContentType) throws IOException {
        byte[] parsedContent = httpData.get();
        TypedContent file = TypedContent.identity(ByteSource.wrap(parsedContent), partContentType);
        String name = httpData.getName();
        ContentDisposition disposition = ContentDisposition.builder("form-data")
                .filename(maybeGetFilename(httpData))
                .name(name)
                .build();
        Multimap<String, String> headers = ArrayListMultimap.create();
        return new FormDataPart(headers, disposition, file);
    }

    protected void handleFileUpload(FileUpload fileUpload, List<FormDataPart> parts) throws IOException {
        parts.add(toFormDataPart(fileUpload, fileUpload.getContentType()));
    }


}

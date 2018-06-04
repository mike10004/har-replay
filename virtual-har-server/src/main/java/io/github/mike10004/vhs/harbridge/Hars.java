package io.github.mike10004.vhs.harbridge;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.repackaged.org.apache.http.NameValuePair;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Static utility methods relating to HAR data.
 */
public class Hars {

    private static final Logger log = LoggerFactory.getLogger(Hars.class);

    static final MediaType CONTENT_TYPE_DEFAULT_VALUE = MediaType.OCTET_STREAM;

    // TODO maybe allow line breaks within base-64 strings
    private static final Pattern BASE_64_PATTERN = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$");

    private static final Charset DEFAULT_WWW_FORM_DATA_CHARSET = StandardCharsets.UTF_8;

    // https://stackoverflow.com/a/8571649/2657036
    private static boolean isValidBase64Encoding(String text) {
        return text != null && BASE_64_PATTERN.matcher(text).find();
    }

    /**
     * Determines (to the extent possible) whether some HAR content is base64-encoded.
     * Designed for arguments taken from a HAR content or post-data object.
     * @param contentType the content type (required)
     * @param text the text
     * @param harContentEncoding the encoding field (of HAR content)
     * @param bodySize the size field
     * @return true if the text represents base-64-encoded data
     */
    public static boolean isBase64Encoded(String contentType, String text, @Nullable String harContentEncoding, @Nullable Long bodySize) {
        if ("base64".equalsIgnoreCase(harContentEncoding)) {
            return true;
        }
        if (text.length() == 0) {
            return true; // because it won't matter, nothing will be decoded
        }
        boolean validBase64 = isValidBase64Encoding(text);
        if (!validBase64) {
            return false;
        }
        if (!ContentTypes.isTextLike(contentType)) {
            return true;
        }
        if (bodySize != null) {
            /*
             * There are cases where the text is not base64-encoded and the content length
             * in bytes is greater than the text length, because some text characters encode
             * as more than one byte. But if the text length is greater than the reported byte
             * length, it's either an incorrectly-reported length value or malformed text, and
             * in either case we assume it's base64-encoding.
             */
            if (text.length() > bodySize) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param params
     * @param contentType
     * @param postDataText
     * @param requestBodySize
     * @param postDataComment
     * @param defaultCharset charset that will be applied if content-type does not specify one
     * @return
     * @throws IOException
     */
    @Nullable
    public static ByteSource getRequestPostData(@Nullable List<NameValuePair> params, String contentType, String postDataText, @Nullable Long requestBodySize, @Nullable String postDataComment, Charset defaultCharset) throws IOException {
        // by the HAR spec, a postData object contains (params XOR text)
        if (params != null && !params.isEmpty()) {
            if (Strings.isNullOrEmpty(contentType)) {
                contentType = MediaType.FORM_DATA.toString();
            }
            MediaType mediaType = MediaType.parse(contentType);
            return toByteSourceFromPairs(params, mediaType);
        } else {
            return translateRequestContent(contentType, postDataText, requestBodySize, null, postDataComment, defaultCharset);
        }
    }

    private static ByteSource toByteSourceFromPairs(List<NameValuePair> params, MediaType contentType) {
        Charset charset = contentType.charset().or(DEFAULT_WWW_FORM_DATA_CHARSET);
        String formString = URLEncodedUtils.format(params, charset);
        return CharSource.wrap(formString).asByteSource(charset);
    }

    /**
     * Encodes HAR response content data or request post-data as bytes.
     * If the data is base-64-encoded, then this just decodes it.
     * If the data is a string, then this encodes that in the charset specified by the content-type.
     *
     * @param contentType the content type (required)
     * @param text the content or POST-data text
     * @param requestBodySize the content length or size field
     * @param harContentEncoding the encoding field (from HAR content object)
     * @param comment the comment
     * @return a byte array containing encoded
     */
    private static ByteSource translateRequestContent(String contentType,
                                                      @Nullable String text,
                                                      @Nullable Long requestBodySize,
                                                      @SuppressWarnings("SameParameterValue") @Nullable String harContentEncoding,
                                                      @SuppressWarnings("unused") @Nullable String comment,
                                                      Charset defaultCharset) {
        return getUncompressedContent(contentType, text, requestBodySize, null, null, harContentEncoding, comment, defaultCharset).asByteSource();
    }

    /**
     * Transforms a byte source such that the returned source decodes data as stipulated
     * @param base64Data base-64-encoded data
     * @param contentEncodingHeaderValue value of HTTP content-encoding header in response
     * @param bodySize bodySize field of HAR response object (compressed size)
     * @param contentSize size field of HAR content object (uncompressed size)
     * @return a byte source supplying decoded data
     */
    @VisibleForTesting
    static ByteSource decodingSource(String base64Data, @Nullable String contentEncodingHeaderValue, @Nullable String harContentEncodingFieldValue, @Nullable Long bodySize, @Nullable Long contentSize) {
        return decodingSource(base64Data, contentEncodingHeaderValue, harContentEncodingFieldValue, bodySize, contentSize, INACTIVE_CONSUMER);
    }

    private static final Consumer<Boolean> INACTIVE_CONSUMER = value -> {};

    @VisibleForTesting
    static ByteSource decodingSource(String base64Data, @Nullable String contentEncodingHeaderValue, @Nullable String harContentEncodingFieldValue, @Nullable Long bodySize, @Nullable Long contentSize, Consumer<? extends Boolean> readabilityTestResultConsumer) {
        Base64ByteSource textAsByteSource = base64DecodingSource(base64Data);
        if (contentEncodingHeaderValue != null) {
            List<String> contentEncodings = HttpContentCodecs.parseEncodings(contentEncodingHeaderValue);
            boolean anyNonIdentity = contentEncodings.stream().anyMatch(encoding -> !HttpContentCodecs.CONTENT_ENCODING_IDENTITY.equalsIgnoreCase(encoding));
            if (anyNonIdentity) {
                ByteSource decodingSource = wrap(textAsByteSource, contentEncodings);
                if (isReadable(decodingSource, 16)) {
                    return decodingSource;
                }
            }
        }
        return textAsByteSource;
    }

    @SuppressWarnings("SameParameterValue")
    static boolean isReadable(@Nullable ByteSource byteSource, long testLength) {
        if (byteSource != null) {
            try {
                try (InputStream in = byteSource.slice(0, testLength).openStream()) {
                    ByteStreams.exhaust(in);
                }
                return true;
            } catch (IOException e) {
                log.debug("byte source not readable due to {}", e.toString());
            }
        }
        return false;
    }

    static Base64ByteSource base64DecodingSource(String base64Data) {
        return Base64ByteSource.wrap(base64Data);
    }

    @Nullable
    static ByteSource wrap(ByteSource original, List<String> contentEncodings) {
        for (String contentEncoding : contentEncodings) {
            @Nullable HttpContentCodec codec = HttpContentCodecs.getCodec(contentEncoding);
            if (codec == null) {
                log.info("unsupported codec: {}", contentEncoding);
                return null;
            }
            original = codec.decompressingSource(original);
        }
        return original;
    }

    /**
     * Gets the uncompressed, unencoded data that constitutes the body of a response captured
     * in a HAR entry, given the various HAR response and HAR content fields that describe it.
     * @param contentType content MIME type
     * @param text data
     * @param bodySize Size of the received response body in bytes.
     *                 Set to zero in case of responses coming from the cache (304).
     *                 Set to -1 if the info is not available.
     * @param contentSize Length of the returned content in bytes.
     *                    Should be equal to response.bodySize if there is no compression
     *                    and bigger when the content has been compressed.
     * @param contentEncodingHeaderValue value of the Content-Encoding header
     * @param harContentEncoding value of the HAR content "encoding" field
     * @param comment HAR content comment
     * @param defaultCharset charset that will be applied to return value if content-type does not specify one
     * @return a byte source that supplies a stream of the response body as unencoded, uncompressed bytes
     */
    public static TypedContent translateResponseContent(              String contentType,
                                                                      @Nullable String text,
                                                                      @Nullable Long bodySize,
                                                                      @Nullable Long contentSize,
                                                                      @Nullable String contentEncodingHeaderValue,
                                                                      @Nullable String harContentEncoding,
                                                                      @SuppressWarnings("unused") @Nullable String comment,
                                                                      Charset defaultCharset) {
        return getUncompressedContent(contentType, text, bodySize, contentSize, contentEncodingHeaderValue, harContentEncoding, comment, defaultCharset);
    }

    static TypedContent getUncompressedContent(@Nullable String contentType,
                                               @Nullable String text,
                                               @Nullable Long bodySize,
                                               @Nullable Long contentSize,
                                               @Nullable String contentEncodingHeaderValue,
                                               @Nullable String harContentEncoding,
                                               @SuppressWarnings("unused") @Nullable String comment,
                                               Charset defaultCharset) {
        contentType = Strings.nullToEmpty(contentType).trim();
        if (contentType.isEmpty()) {
            contentType = MediaType.OCTET_STREAM.toString();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parse(contentType);
        } catch (RuntimeException e) {
            log.info("failed to parse content-type \"{}\"", StringEscapeUtils.escapeJava(contentType));
            mediaType = MediaType.OCTET_STREAM;
        }
        if (text == null) {
            return TypedContent.identity(ByteSource.empty(), mediaType);
        }
        boolean base64 = isBase64Encoded(contentType, text, harContentEncoding, bodySize);
        if (base64) {
            ByteSource decodedDataSource = decodingSource(text, contentEncodingHeaderValue, harContentEncoding, bodySize, contentSize);
            return TypedContent.identity(decodedDataSource, mediaType);
        } else {
            @Nullable Charset charset_ = null;
            try {
                charset_ = MediaType.parse(contentType).charset().orNull();
            } catch (RuntimeException ignore) {
            }
            Charset adjustedCharset = adjustCharset(text, charset_, defaultCharset);
            ByteSource data = CharSource.wrap(text).asByteSource(adjustedCharset);
            MediaType adjustedContentType = mediaType.withCharset(adjustedCharset);
            return TypedContent.identity(data, adjustedContentType);
        }
    }

    static Charset adjustCharset(String text, @Nullable Charset charset, Charset defaultCharset) {
        requireNonNull(defaultCharset, "default charset");
        text = Strings.nullToEmpty(text);
        if (charset == null) {
            return defaultCharset;
        }
        CharsetEncoder encoder = charset.newEncoder();
        if (encoder.canEncode(text)) {
            return charset;
        }
        return StandardCharsets.UTF_8;
    }
}

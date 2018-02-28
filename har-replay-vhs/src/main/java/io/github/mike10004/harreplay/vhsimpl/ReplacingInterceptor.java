package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplayServerConfig.RegexHolder;
import io.github.mike10004.harreplay.ReplayServerConfig.Replacement;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import io.github.mike10004.harreplay.VariableDictionary;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ImmutableHttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.harbridge.HttpContentCodec;
import io.github.mike10004.vhs.harbridge.HttpContentCodecs;
import io.github.mike10004.vhs.nanohttpd.ResponseInterceptor;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public class ReplacingInterceptor implements ResponseInterceptor {

    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // future: allow some configuration of replacement actions, such as ignoring content type
    private final VhsReplayManagerConfig config;
    private final Replacement replacement;

    public ReplacingInterceptor(VhsReplayManagerConfig config, Replacement replacement) {
        this.config = requireNonNull(config, "config");
        this.replacement = requireNonNull(replacement, "replacement");
        requireNonNull(replacement.match, "replacement.match");
        requireNonNull(replacement.replace, "replacement.replace");
    }

    @Override
    public HttpRespondable intercept(ParsedRequest parsedRequest, HttpRespondable httpRespondable) {
        @Nullable MediaType contentType = httpRespondable.previewContentType();
        if (!isTextType(contentType)) {
            return httpRespondable;
        }
        try {
            String text = collectText(httpRespondable);
            AtomicInteger counter = new AtomicInteger(0);
            String replaced = doReplacing(parsedRequest, text, counter);
            if (counter.get() == 0) {
                return httpRespondable;
            }
            Charset charset = contentType.charset().or(DEFAULT_INTERNET_TEXT_CHARSET);
            ImmutableHttpRespondable.Builder b = ImmutableHttpRespondable.builder(httpRespondable.getStatus());
            b.bodySource(CharSource.wrap(replaced).asByteSource(charset));
            httpRespondable.streamHeaders().filter(notHeaderName(HttpHeaders.CONTENT_ENCODING)).forEach(b::header);
            b.contentType(contentType);
            return b.build();
        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).info("failed to read text in response; not performing replacements", e);
            return httpRespondable;
        }
    }

    protected static Predicate<Entry<String, String>> notHeaderName(String headerName) {
        return stringStringEntry -> !headerName.equalsIgnoreCase(stringStringEntry.getKey());
    }

    protected String doReplacing(ParsedRequest request, String source, AtomicInteger counter) {
        if (source.isEmpty()) {
            return source;
        }
        Pattern pattern;
        if (replacement.match instanceof StringLiteral) {
            pattern = Pattern.compile(Pattern.quote(((StringLiteral)replacement.match).value));
        } else if (replacement.match instanceof ReplayServerConfig.RegexHolder){
            pattern = Pattern.compile(((RegexHolder)replacement.match).regex);
        } else {
            throw new IllegalArgumentException("not sure how to handle replacment match of this type: " + replacement.match);
        }
        Matcher m = pattern.matcher(source);
        VariableDictionary dictionary = new ReplacingInterceptorVariableDictionary(request);
        String replacementText = replacement.replace.interpolate(dictionary);
        String textWithReplacements = m.replaceAll(replacementText);
        if (!source.equals(textWithReplacements)) {
            // TODO actually count the replacements
            counter.incrementAndGet();
        }
        return textWithReplacements;
    }

    private static class FlushedContent {
        public final MediaType contentType;
        public final byte[] data;

        private FlushedContent(MediaType contentType, byte[] data) {
            this.contentType = contentType;
            this.data = data;
        }
    }

    protected interface WritingAction<T> {
        T write(OutputStream outputStream) throws IOException;
    }

    protected static class WritingActionResult<T> {
        public final T actionReturnValue;
        public final byte[] byteArray;

        public WritingActionResult(T actionReturnValue, byte[] byteArray) {
            this.actionReturnValue = actionReturnValue;
            this.byteArray = byteArray;
        }
    }

    protected static <T> WritingActionResult<T> writeByteArray(WritingAction<T> action, int expectedOutputLength) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(expectedOutputLength);
        T returnValue = action.write(baos);
        baos.flush();
        byte[] data = baos.toByteArray();
        return new WritingActionResult<>(returnValue, data);
    }

    protected static FlushedContent toByteArray(HttpRespondable respondable) throws IOException {
        HeaderList hlist = HeaderList.from(respondable.streamHeaders());
        String contentEncodingHeaderValue = hlist.getFirstValue(HttpHeaders.CONTENT_ENCODING);
        List<String> contentEncodings = HttpContentCodecs.parseEncodings(contentEncodingHeaderValue);
        WritingActionResult<MediaType> writeResult = writeByteArray(respondable::writeBody, 256);
        byte[] data = writeResult.byteArray;
        for (String encoding : contentEncodings) {
            HttpContentCodec codec = HttpContentCodecs.getCodec(encoding);
            if (codec == null) {
                throw new IOException("can't decompress with codec " + encoding);
            }
            data = codec.decompress(data);
        }
        return new FlushedContent(writeResult.actionReturnValue, data);
    }

    private static final Charset DEFAULT_INTERNET_TEXT_CHARSET = StandardCharsets.ISO_8859_1;

    protected static String collectText(HttpRespondable respondable) throws IOException {
        FlushedContent content = toByteArray(respondable);
        Charset charset = content.contentType.charset().or(DEFAULT_INTERNET_TEXT_CHARSET);
        return charset.newDecoder().decode(ByteBuffer.wrap(content.data)).toString();
    }

    protected static boolean isTextType(@Nullable MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        if (contentType.is(MediaType.ANY_TEXT_TYPE)) {
            return true;
        }
        MediaType noCharsetType = contentType.withoutParameters();
        for (MediaType textLike : parameterlessTextLikeTypes) {
            if (textLike.equals(noCharsetType)) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    static final ImmutableSet<MediaType> parameterlessTextLikeTypes = ImmutableSet.<MediaType>builder()
            .add(MediaType.JSON_UTF_8.withoutParameters())
            .add(MediaType.JAVASCRIPT_UTF_8.withoutParameters())
            .add(MediaType.XML_UTF_8.withoutParameters())
            .add(MediaType.APPLICATION_XML_UTF_8.withoutParameters())
            .add(MediaType.XHTML_UTF_8.withoutParameters())
            .build();
}

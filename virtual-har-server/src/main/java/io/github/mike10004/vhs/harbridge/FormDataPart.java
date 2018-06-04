package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import javax.annotation.Nullable;

public class FormDataPart {

    public final ImmutableMultimap<String, String> headers;

    @Nullable
    public final ContentDisposition contentDisposition;

    @Nullable
    public final TypedContent file;

    public FormDataPart(Multimap<String, String> headers, @Nullable ContentDisposition contentDisposition, @Nullable TypedContent file) {
        this.headers = ImmutableMultimap.copyOf(headers);
        this.contentDisposition = contentDisposition;
        this.file = file;
    }

    @Override
    public String toString() {
        return "FormDataPart{" +
                "headers.size=" + headers.size() +
                ", contentDisposition=" + quote(contentDisposition) +
                ", file=" + file +
                '}';
    }

    @Nullable
    private static String quote(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return String.format("\"%s\"", StringEscapeUtils.escapeJava(StringUtils.abbreviateMiddle(value.toString(), "[...]", 64)));
    }
}

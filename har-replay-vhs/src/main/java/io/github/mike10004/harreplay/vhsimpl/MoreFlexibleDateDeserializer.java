package io.github.mike10004.harreplay.vhsimpl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Stream;

public class MoreFlexibleDateDeserializer extends StdDeserializer<Date> {

    private static final ImmutableList<DateFormat> DEFAULT_SUPPORTED_DATE_FORMATS =
            Stream.of("yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "EEE, dd MMM yyyy HH:mm:ss zzz",
                    "MMM dd, yyyy h:mm:ss a",         // Feb 16, 2018 4:41:27 PM
                    "yyyy-MM-dd")
            .map(SimpleDateFormat::new)
            .collect(ImmutableList.toImmutableList());

    private final ImmutableList<DateFormat> alternativeFormats;

    public MoreFlexibleDateDeserializer(Iterable<DateFormat> alternativeFormats) {
        super(Date.class);
        this.alternativeFormats = ImmutableList.copyOf(alternativeFormats);
    }

    public MoreFlexibleDateDeserializer() {
        this(DEFAULT_SUPPORTED_DATE_FORMATS);
    }

    protected Stream<DateFormat> streamSupportedFormats(DeserializationContext ctxt) {
        DateFormat contextFormat = ctxt.getConfig().getDateFormat();
        return Stream.concat(Stream.of(contextFormat), alternativeFormats.stream());
    }

    @Override
    public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.getCurrentToken();
        if (token == JsonToken.VALUE_STRING) {
            String dateStr = p.getValueAsString();
            @Nullable Date date = streamSupportedFormats(ctxt)
                    .map(dateFormat -> {
                        try {
                            return dateFormat.parse(dateStr);
                        } catch (ParseException ignore) {
                            return null;
                        }
                    }).filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (date != null) {
                return date;
            }
        }
        return super._parseDate(p, ctxt);
    }
}

package io.github.mike10004.harreplay.exec;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Stream;

public class SstoehrHarCleaningTransform implements HarTransform {

    private SstoehrHarCleaningTransform() {}

    @Override
    public CharSource transform(CharSource originalHar) throws IOException {
        JsonObject har;
        try (Reader reader = originalHar.openStream()) {
            har = new JsonParser().parse(reader).getAsJsonObject();
        }
        fixPageStartedDateTime(har);
        fixEntryStartedDateTime(har);
        fixResponseCookieExpires(har);
        return repackage(har);
    }

    public static HarTransform inMemory() {
        return new SstoehrHarCleaningTransform();
    }

    public static HarTransform onDisk(Path scratchDir) {
        return new SstoehrHarCleaningTransform() {

            private final Charset charset =  StandardCharsets.UTF_8;

            @Override
            protected CharSource repackage(JsonObject har) throws IOException {
                File file = File.createTempFile("sstoehr-cleaned", ".har", scratchDir.toFile());
                try (Writer out = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                    new Gson().toJson(har, out);
                }
                return Files.asCharSource(file, charset);
            }
        };
    }

    protected CharSource repackage(JsonObject har) throws IOException {
        return CharSource.wrap(har.toString());
    }

    private void fixPageStartedDateTime(JsonObject har) {
        // reference chain: de.sstoehr.harreader.model.Har["log"]
        // ->de.sstoehr.harreader.model.HarLog["pages"]
        // ->java.util.ArrayList[0]
        // ->de.sstoehr.harreader.model.HarPage["startedDateTime"]
        JsonObject log = getAsObject(har, "log");
        if (log != null) {
            JsonArray pages = log.getAsJsonArray("pages");
            if (pages != null) {
                for (int i = 0; i < pages.size(); i++) {
                    JsonElement pageEl = pages.get(i);
                    cleanDateChild(pageEl, "startedDateTime");
                }
            }
        }
    }

    private void fixEntryStartedDateTime(JsonObject har) {
        // reference chain: de.sstoehr.harreader.model.Har["log"]
        // ->de.sstoehr.harreader.model.HarLog["pages"]
        // ->java.util.ArrayList[0]
        // ->de.sstoehr.harreader.model.HarPage["startedDateTime"]
        JsonObject log = getAsObject(har, "log");
        if (log != null) {
            JsonArray entries = log.getAsJsonArray("entries");
            if (entries != null) {
                for (int i = 0; i < entries.size(); i++) {
                    JsonElement pageEl = entries.get(i);
                    cleanDateChild(pageEl, "startedDateTime");
                }
            }
        }
    }

    private void fixResponseCookieExpires(JsonObject har) {
        JsonObject log = getAsObject(har, "log");
        if (log != null) {
            JsonArray entries = log.getAsJsonArray("entries");
            if (entries != null) {
                for (int i = 0; i < entries.size(); i++) {
                    JsonElement entryEl = entries.get(i);
                    JsonObject responseObj = entryEl.getAsJsonObject().getAsJsonObject("response");
                    JsonArray cookiesArr = responseObj.getAsJsonArray("cookies");
                    for (JsonElement cookieEl : cookiesArr) {
                        cleanDateChild(cookieEl, "expires");
                    }
                }
            }
        }
    }

    static boolean isCleanAlready(String dateStr) {
        for (DateFormat fmt : JACKSON_SUPPORTED_DATE_FORMATS) {
            try {
                fmt.parse(dateStr);
                return true;
            } catch (ParseException ignore) {
            }
        }
        return false;
    }

    static String cleanDate(String dateStr) {
        for (DateFormat dirtyFmt : DIRTY_FORMATS) {
            try {
                Date date = dirtyFmt.parse(dateStr);
                return CLEAN_DATE_FORMAT.format(date);
            } catch (ParseException ignore) {
            }
        }
        return dateStr;
    }

    static void cleanDateChild(JsonElement parent, String fieldName) {
        JsonObject pageObj = parent.getAsJsonObject();
        JsonElement dateEl = pageObj.get(fieldName);
        if (dateEl != null) {
            String dateStr = dateEl.getAsString();
            if (!isCleanAlready(dateStr)) {
                dateStr = cleanDate(dateStr);
                pageObj.add(fieldName, new JsonPrimitive(dateStr));
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    @Nullable
    private static JsonObject getAsObject(JsonObject parent, String memberName) {
        return parent.getAsJsonObject(memberName);
    }

    private static final DateFormat CLEAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static final ImmutableList<DateFormat> JACKSON_SUPPORTED_DATE_FORMATS = Stream.of("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd")
            .map(SimpleDateFormat::new)
            .collect(ImmutableList.toImmutableList());

    // Feb 16, 2018 4:41:27 PM
    private static final ImmutableList<DateFormat> DIRTY_FORMATS = Stream.of("MMM dd, yyyy h:mm:ss a")
            .map(SimpleDateFormat::new)
            .collect(ImmutableList.toImmutableList());
}

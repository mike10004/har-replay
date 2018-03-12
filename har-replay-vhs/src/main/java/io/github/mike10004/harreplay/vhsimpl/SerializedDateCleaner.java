package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Stream;

class SerializedDateCleaner {

    private SerializedDateCleaner() {}

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

    private static final DateFormat CLEAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static final ImmutableList<DateFormat> JACKSON_SUPPORTED_DATE_FORMATS = Stream.of("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd")
            .map(SimpleDateFormat::new)
            .collect(ImmutableList.toImmutableList());

    // Feb 16, 2018 4:41:27 PM
    private static final ImmutableList<DateFormat> DIRTY_FORMATS = Stream.of("MMM dd, yyyy h:mm:ss a")
            .map(SimpleDateFormat::new)
            .collect(ImmutableList.toImmutableList());
}


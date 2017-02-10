package com.github.mike10004.harreplay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.lightbody.bmp.core.har.Har;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.Assert.assertEquals;

public class HarIO {

    private HarIO() {}

    public static Har fromFile(File harFile) throws IOException {
        Har har;
        try (Reader reader = new FileReader(harFile)) {
            har = harGson.fromJson(reader, Har.class);
        }
        return har;
    }

    private static final Gson harGson = buildHarGson();

    public static Gson buildHarGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Long.TYPE, FlexibleLongDeserializer.getInstance())
                .registerTypeAdapter(Long.class, FlexibleLongDeserializer.getInstance())
                .create();
    }

    static class FlexibleLongDeserializer implements JsonDeserializer<Long> {

        private static final JsonDeserializer<Long> instance = new FlexibleLongDeserializer();

        public static JsonDeserializer<Long> getInstance() {
            return instance;
        }

        @Override
        public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonPrimitive()) {
                throw new JsonParseException("not a number: " + json);
            }
            JsonPrimitive prim = json.getAsJsonPrimitive();
            if (!prim.isNumber()) {
                throw new JsonParseException("not a number: " + prim);
            }
            BigDecimal decimal = prim.getAsBigDecimal();
            long longValue = decimal.longValue();
            if (!decimal.equals(BigDecimal.valueOf(longValue))) {
                return Math.round(decimal.doubleValue());
            } else {
                return longValue;
            }
        }
    }

    public static class RoundingLongDeserializerTest {
        @Test
        public void deserialize_roundDown() throws Exception {
            String json = "389.0429998282343";
            long actual = buildHarGson().fromJson(json, Long.class);
            assertEquals("rounded down", 389L, actual);
        }
        @Test
        public void deserialize_roundUp() throws Exception {
            String json = "389.5429998282343";
            long actual = buildHarGson().fromJson(json, Long.class);
            assertEquals("rounded up", 390L, actual);
        }
        @Test
        public void deserialize_unnecessary() throws Exception {
            String json = "389";
            long actual = buildHarGson().fromJson(json, Long.class);
            assertEquals("already integer", 389L, actual);
        }

        public static class Widget {
            long value;
        }

        @Test
        public void deserialize_field() throws Exception {
            String json = "{\"value\": 389.0429998282343}";
            Widget widget = buildHarGson().fromJson(json, Widget.class);
            assertEquals(389L, widget.value);
        }
    }
}

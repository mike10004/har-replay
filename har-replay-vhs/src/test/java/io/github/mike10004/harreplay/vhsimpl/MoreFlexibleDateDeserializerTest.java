package io.github.mike10004.harreplay.vhsimpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MoreFlexibleDateDeserializerTest {

    private final TestCase testCase;

    public MoreFlexibleDateDeserializerTest(TestCase testCase) {
        this.testCase = testCase;
    }

    @Parameters
    public static List<TestCase> testCases() {
        return Arrays.asList(
                TestCase.of("2018-03-12T10:44:05.37-04:00", "2018-03-12T10:44:05.37-04:00"),
                TestCase.of("Feb 16, 2018 4:41:27 PM", "2018-02-16T16:41:27-05:00"),
                TestCase.of(new Date().getTime()),
                new TestCase(JsonNull.INSTANCE, null, ex -> false),
                TestCase.of("turtle", com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
        );
    }

    @Test
    public void parseDate() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Date.class, new MoreFlexibleDateDeserializer());
        mapper.registerModule(module);
        String json = testCase.input.toString();
        try (Reader reader = new StringReader(json)){
            Date actual = mapper.readValue(reader, Date.class);
            System.out.format("%s -> %s%n", json, testCase.valueExpectation);
            assertEquals(String.format("expect %s -> %s", json, testCase.valueExpectation), testCase.valueExpectation, actual);
        } catch (Exception e) {
            System.out.format("parsing %s threw %s%n", json, StringUtils.abbreviate(e.toString(), 64));
            assertTrue("exception thrown must pass " + testCase.exceptionExpectation + " but it is " + e, testCase.exceptionExpectation.test(e));
        }

    }

    private static class TestCase {
        public final JsonElement input;
        @Nullable
        public final Date valueExpectation;

        public final Predicate<? super Exception> exceptionExpectation;

        public TestCase(JsonElement input, @Nullable Date valueExpectation, Predicate<? super Exception> exceptionExpectation) {
            this.input = input;
            this.valueExpectation = valueExpectation;
            this.exceptionExpectation = exceptionExpectation;
        }

        public static TestCase of(String input, Date date) {
            return new TestCase(new JsonPrimitive(input), date, ex -> false);
        }

        public static TestCase of(String input, String iso8601Date) {
            TemporalAccessor ta = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("America/New_York")).parse(iso8601Date);
            Instant instant = Instant.from(ta);
            return of(input, Date.from(instant));
        }

        public static TestCase of(long input) {
            return of(input, new Date(input));
        }

        public static TestCase of(long input, Date date) {
            return new TestCase(new JsonPrimitive(input), date, ex -> false);
        }

        public static TestCase of(String input, Class<? extends Exception> expectedExceptionType) {
            return new TestCase(new JsonPrimitive(input), null, expectedExceptionType::isInstance);
        }
    }
}
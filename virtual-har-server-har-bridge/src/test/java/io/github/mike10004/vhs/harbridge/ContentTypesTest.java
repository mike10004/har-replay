package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nullable;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ContentTypesTest {

    private final TestCase testCase;

    public ContentTypesTest(TestCase testCase) {
        this.testCase = testCase;
    }

    @Test
    public void isTextLike() {
        boolean actual = ContentTypes.isTextLike(testCase.contentType);
        String message = String.format("expect text-like %s for \"%s\"", testCase.expected, StringEscapeUtils.escapeJava(testCase.contentType));
        System.out.format("%s; actual = %s%n", message, actual);
        assertEquals(message, testCase.expected, actual);
        try {
            if (testCase.contentType != null) {
                boolean actualWithoutParams = ContentTypes.isTextLike(MediaType.parse(testCase.contentType).toString());
                assertEquals(message + " without parameters", testCase.expected, actualWithoutParams);
            }
        } catch (IllegalArgumentException ignore) {
        }
    }

    @Parameterized.Parameters
    public static List<TestCase> testCases() {
        return ImmutableList.copyOf(new TestCase[]{
                new TestCase(null, false),
                new TestCase("", false),
                new TestCase(" ", false),
                TestCase.no(MediaType.OCTET_STREAM),
                TestCase.no(MediaType.PDF),
                TestCase.no(MediaType.PNG),
                TestCase.no(MediaType.JPEG),
                TestCase.yes(MediaType.PLAIN_TEXT_UTF_8),
                TestCase.yes(MediaType.parse("image/svg+xml")),
                TestCase.yes(MediaType.XML_UTF_8),
                TestCase.yes(MediaType.APPLICATION_XML_UTF_8),
                TestCase.yes(MediaType.CSS_UTF_8),
                TestCase.yes(MediaType.CSV_UTF_8),
                TestCase.yes(MediaType.JAVASCRIPT_UTF_8),
                TestCase.yes(MediaType.TEXT_JAVASCRIPT_UTF_8),
                TestCase.yes(MediaType.HTML_UTF_8),
                TestCase.yes(MediaType.XHTML_UTF_8),
                TestCase.yes(MediaType.FORM_DATA),
                TestCase.yes(MediaType.ATOM_UTF_8),
                TestCase.yes(MediaType.DART_UTF_8),
                TestCase.yes(MediaType.JSON_UTF_8),
                TestCase.yes(MediaType.MANIFEST_JSON_UTF_8),
                TestCase.yes(MediaType.RTF_UTF_8),
                TestCase.yes(MediaType.SOAP_XML_UTF_8),
                TestCase.yes(MediaType.TSV_UTF_8),
                TestCase.yes(MediaType.XRD_UTF_8),
        });
    }

    private static class TestCase {
        @Nullable
        public final String contentType;
        public final boolean expected;

        public TestCase(@Nullable String contentType, boolean expected) {
            this.contentType = contentType;
            this.expected = expected;
        }

        public static TestCase of(MediaType mediaType, boolean expected) {
            return new TestCase(mediaType.toString(), expected);
        }

        public static TestCase yes(MediaType mediaType) {
            return of(mediaType, true);
        }

        public static TestCase no(MediaType mediaType) {
            return of(mediaType, false);
        }
    }
}
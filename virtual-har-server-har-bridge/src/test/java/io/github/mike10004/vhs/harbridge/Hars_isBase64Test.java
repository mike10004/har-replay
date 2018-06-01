package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nullable;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class Hars_isBase64Test {

    private final TestCase testCase;

    public Hars_isBase64Test(TestCase testCase) {
        this.testCase = testCase;
    }

    @Parameterized.Parameters
    public static List<TestCase> testCases() {
        return ImmutableList.copyOf(new TestCase[]{
                new TestCase("text/plain", "abcd", null, 4L, false),
                new TestCase("text/plain", "abcd", null, null, false),
                new TestCase("text/plain", "abcdabcdabcd", null, 9L, true),
                new TestCase("text/plain", "abcdabcdabcd", null, 12L, false),
                new TestCase("text/plain", "abcd", "base64", null, true),
                new TestCase("text/plain", "abcd", "base64", 3L, true),
                new TestCase("text/plain", "abcd", "base64", null, true),
                new TestCase("application/octet-stream", "abcd", null, null, true),
                new TestCase("application/x-not-already-known", "ThisIsAllBase64Alphabet", null, null, false),
                new TestCase("application/x-not-already-known", "ThisIsAllBase64Alphabet", "base64", null, true),
                new TestCase("multipart/mixed; boundary=ABCDEF_1522096137171",
                        "--ABCDEF_1522096137171\r\n" +
                            "Content-Type: application/x-www-form-urlencoded\r\n" +
                            "\r\n" +
                            "foo=bar&baz=gaw\r\n" +
                            "--ABCDEF_1522096137171--", null, 116L, false),
                new TestCase("application/json", "h3MImbYGI5d3iZmLtEPUHWVevJGrhhJk+ZHOJmLFLtYsXvv5IwIUSYP5/NLJ8EbVuhsw86plr6V0NpDZLrVnSKViDRJQCcPo1SSde2Va2b5uTTE+0TqkOq0vCS+akR9U9oBS3vfFu1aUO+pcTU4BhDArQbQ8H7gChSZiu56rRlEXuWxKshTlJvjwET7KYRlU4nc6iicp01kw5G0l9Y+4CHaViL6D/Xan0Un93yne+IocNHxbdWyLC7WIJf6eOxZLdIVwy048h/b/uslCO73sNKggyNMsiyHebxEFDlxgAgDjB8HyWlluTPeX17zVtSO1BBF/JPfVTr2OLwK2N7nx8Y+lh4DVUYdnRdHHYlFY36PjDSUFovyGd5duFz/5bgEvsrs=", "base64", 290L, true),
                new TestCase("application/json", "{\"apples\":1156,\"peaches\":4689236,\"pumpkin\":3275175,\"pie\":1235856}", null, 32L, false),
        });
    }


    @Test
    public void isBase64Encoded() {
        System.out.format("isBase64Encoded%n%s%n%n", testCase);
        boolean actual = Hars.isBase64Encoded(testCase.contentType, testCase.text, testCase.encoding, testCase.size);
        assertEquals(testCase.toString(), testCase.expected, actual);
        if (testCase.expected) {
            BaseEncoding.base64().decode(testCase.text); // make sure no exception thrown
        }
    }

    private static class TestCase {
        public final String contentType, text, encoding;
        public final Long size;
        public final boolean expected;

        public TestCase(String contentType, String text, String encoding, Long size, boolean expected) {
            this.contentType = contentType;
            this.text = text;
            this.encoding = encoding;
            this.size = size;
            this.expected = expected;
        }

        @Override
        public String toString() {
            return "Base64TestCase{" +
                    "contentType=" + quote(contentType) +
                    ", text=" + quote(text) +
                    ", encoding=" + quote(encoding) +
                    ", size=" + size +
                    ", expected=" + expected +
                    '}';
        }

        private static String quote(@Nullable String value) {
            if (value == null) {
                return null;
            }
            return "\"" + StringEscapeUtils.escapeJava(StringUtils.abbreviateMiddle(value, "[...]", 128)) + "\"";
        }
    }

}

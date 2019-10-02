package io.github.mike10004.harreplay.exec;

import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import com.opencsv.CSVReader;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.harreplay.exec.HarInfoDumper.SummaryDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.TerseDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.VerboseDumper;
import io.github.mike10004.harreplay.tests.Fixtures;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Enclosed.class)
public class HarInfoDumperTest {

    public static class HarReaderDependencyTest {

        @Test
        public void testRead() throws Exception {
            de.sstoehr.harreader.model.Har har = new de.sstoehr.harreader.HarReader().readFromString("{}");
            assertNotNull(har);
        }

    }

    public static class $Test {

        @ClassRule
        public static FixturesRule fixturesRule = Fixtures.asRule();

        @ClassRule
        public static TemporaryFolder temporaryFolder = new TemporaryFolder();

        @Test
        public void summary() throws Exception {
            String output = dump(new SummaryDumper());
            System.out.println(output);
        }

        @Test
        public void terse() throws Exception {
            String output = dump(new TerseDumper());
            System.out.println(output);
        }

        @Test
        public void verbose() throws Exception {
            String output = dump(new VerboseDumper());
            System.out.println(output);
        }

        @Test
        public void csvWithContent() throws Exception {
            File destDir = temporaryFolder.getRoot();
            dump(HarInfoDumper.CsvDumper.makeContentWritingInstance(destDir));
            Collection<File> files = FileUtils.listFiles(destDir, null, false);
            files.forEach(System.out::println);
        }

        private static String dump(HarInfoDumper dumper) throws UnsupportedEncodingException, HarReaderException {
            Charset charset = StandardCharsets.UTF_8;
            System.out.format("%s%n", dumper.getClass().getSimpleName());
            Fixtures f = fixturesRule.getFixtures();
            File harFile = f.http().harFile();
            List<HarEntry> entries = new HarReader().readFromFile(harFile).getLog().getEntries();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
            try (PrintStream out = new PrintStream(baos, true, charset.name())) {
                dumper.dump(entries, out);
            }
            return baos.toString(charset.name());
        }
    }

    public static class CsvDumperTest {

        @Rule
        public TemporaryFolder temp = new TemporaryFolder();

        @Test
        public void createAppendage_get() throws Exception {
            HarInfoDumper.CsvDumper.ContentDumpingRowTransform xform = new HarInfoDumper.CsvDumper.ContentDumpingRowTransform(temp.getRoot());
            HarInfoDumper.CsvDumper.DefaultRowTransform.BasicData basic = new HarInfoDumper.CsvDumper.DefaultRowTransform.BasicData("https://www.blah.com/some?thing=weird", 200, HttpMethod.GET, 5213L, "application/x-javascript; charset=utf-8", null);
            HarEntry entry = makeEntry(HttpMethod.GET, null, null, "console.log(\"hello\");", MediaType.parse(basic.contentType));
            HarInfoDumper.CsvDumper.ContentDumpingRowTransform.Appendage a = xform.createAppendage(entry, 0, basic);
            assertEquals("responseContentPath", "0-response.js", a.responseContentPath.toString());
        }

        private static HarEntry makeEntry(HttpMethod method, @Nullable String postText, @Nullable MediaType postMimeType, String rspText, MediaType rspContentType) {
            HarPostData postData = null;
            if (postText != null) {
                postData = new HarPostData();
                postData.setText(postText);
                postData.setMimeType(postMimeType == null ? null : postMimeType.toString());
            }
            return makeEntry(method, postData, rspText, rspContentType);
        }

        private static HarEntry makeEntry(HttpMethod method, @Nullable HarPostData postData, String rspText, MediaType rspContentType) {
            HarEntry entry = new HarEntry();
            HarResponse response =new HarResponse();
            HarRequest request = new HarRequest();
            entry.setResponse(response);
            entry.setRequest(request);
            request.setUrl("http://www.example.com/post");
            request.setMethod(method);
            request.setPostData(postData);
            HarContent rspContent = new HarContent();
            rspContent.setText(rspText);
            rspContent.setMimeType(rspContentType.toString());
            rspContent.setSize((long) rspText.length());
            response.setContent(rspContent);
            return entry;
        }

        @Test
        public void advanced() throws Exception {
            HarInfoDumper.CsvDumper dumper = (HarInfoDumper.CsvDumper) HarInfoDumper.CsvDumper.makeContentWritingInstance(temp.getRoot());
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            HarEntry entry = makeEntry(HttpMethod.POST, "This is the post content", MediaType.PLAIN_TEXT_UTF_8.withoutParameters(), "This is the response text", MediaType.PLAIN_TEXT_UTF_8.withoutParameters());
            try (PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8.name())) {
                dumper.dump(Collections.singletonList(entry), out);
            }
            String actualText = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            System.out.println(actualText);
            CSVReader reader = new CSVReader(new StringReader(actualText));
            List<String[]> rows = reader.readAll();
            assertEquals("num rows", 2, rows.size());
            String[] actual = rows.get(1);
            String[] expected = {
                    "0","POST","http://www.example.com/post","text/plain","25","","text/plain","0-response.txt","0-request.txt"
            };
            assertArrayEquals("row", expected, actual);
        }

        @Test
        public void createAppendage_postDataWithParams() throws Exception {
            HarPostData postData = new HarPostData();
            HarPostDataParam param = new HarPostDataParam();
            param.setName("foo");
            param.setValue("bar");
            String requestMimeType = "application/x-www-form-urlencoded";
            postData.setParams(Collections.singletonList(param));
            postData.setMimeType(requestMimeType);
            HarEntry entry = makeEntry(HttpMethod.POST, postData, "OK", MediaType.JSON_UTF_8);
            HarInfoDumper.CsvDumper.ContentDumpingRowTransform xform = new HarInfoDumper.CsvDumper.ContentDumpingRowTransform(temp.getRoot());
            HarInfoDumper.CsvDumper.DefaultRowTransform.BasicData basicData = xform.makeBasicData(entry, 0);
            HarInfoDumper.CsvDumper.ContentDumpingRowTransform.Appendage a = xform.createAppendage(entry, 0, basicData);
            assertEquals("filename", "0-request.json", a.requestContentPath.toString());
            assertEquals("request content type", requestMimeType, a.requestContentType);
        }

        @Test
        public void createAppendage_postDataWithText() throws Exception {
            HarEntry entry = makeEntry(HttpMethod.POST, "{\"foo\": \"bar\"}", MediaType.JSON_UTF_8.withoutParameters(), "OK", MediaType.PLAIN_TEXT_UTF_8.withoutParameters());
            HarInfoDumper.CsvDumper.ContentDumpingRowTransform xform = new HarInfoDumper.CsvDumper.ContentDumpingRowTransform(temp.getRoot());
            HarInfoDumper.CsvDumper.DefaultRowTransform.BasicData basicData = xform.makeBasicData(entry, 0);
            HarInfoDumper.CsvDumper.ContentDumpingRowTransform.Appendage a = xform.createAppendage(entry, 0, basicData);
            assertEquals("filename", "0-request.json", a.requestContentPath.toString());
            assertEquals("request content type", MediaType.JSON_UTF_8.withoutParameters().toString(), a.requestContentType);
        }
    }

    @RunWith(Parameterized.class)
    public static class CsvDumper_MimeTypeTest {

        private final TestCase testCase;

        public CsvDumper_MimeTypeTest(TestCase testCase) {
            this.testCase = testCase;
        }

        @Parameterized.Parameters
        public static List<TestCase> testCases() {
            return ImmutableList.copyOf(new TestCase[]{
                    TestCase.of((String) null, "", "null input"),
                    TestCase.of("", "", "empty input"),
                    TestCase.of("application/x-made-up-type", "", "unknown"),
                    TestCase.of(MediaType.JPEG, ".jpg"),
                    TestCase.of(MediaType.PNG, ".png"),
                    TestCase.of("application/json", ".json"),
                    TestCase.of("text/json", ".json"),
                    TestCase.of("text/plain", ".txt"),
                    TestCase.of(MediaType.PLAIN_TEXT_UTF_8, ".txt"),
                    TestCase.of(MediaType.JSON_UTF_8, ".json"),
                    TestCase.of(MediaType.OCTET_STREAM, ""),
                    TestCase.of(MediaType.SVG_UTF_8.withoutParameters(), ".svg"),
                    TestCase.of(MediaType.CSS_UTF_8, ".css"),
                    TestCase.of("video/mp4", ".mp4"),
                    TestCase.of("application/x-javascript; charset=utf-8", ".js"),
                    TestCase.of("text/javascript; charset=utf-8", ".js"),
            });
        }

        @Test
        public void getFilenameSuffixForMimeType() throws Exception {
            String actual = HarInfoDumper.CsvDumper.getFilenameSuffixForMimeType(testCase.contentType);
            assertEquals(testCase.toString(), testCase.expected, actual);
        }

        private static class TestCase {

            @Nullable
            public final String contentType;

            public final String expected;

            @Nullable
            private final String assertionMessage;

            public static TestCase of(String contentType, String expected, String message) {
                return new TestCase(contentType, expected, message);
            }

            public static TestCase of(String contentType, String expected) {
                return of(contentType, expected, null);
            }

            public static TestCase of(MediaType contentType, String expected, String message) {
                return of(contentType.toString(), expected, message);
            }

            public static TestCase of(MediaType contentType, String expected) {
                return of(contentType, expected, null);
            }

            private TestCase(@Nullable String contentType, String expected, @Nullable String assertionMessage) {
                this.contentType = contentType;
                this.expected = expected;
                this.assertionMessage = assertionMessage;
            }

            public String toString() {
                String input = contentType == null ? null : "\"" + StringEscapeUtils.escapeJava(contentType) + "\"";
                String expected_ = StringEscapeUtils.escapeJava(expected);
                return assertionMessage == null
                        ? String.format("TestCase{input=\"%s\", expected=\"%s\"}", input, expected_)
                        : String.format("TestCase{input=%s, expected=\"%s\", message=\"%s\"}", input, expected_, assertionMessage);
            }
        }
    }
}
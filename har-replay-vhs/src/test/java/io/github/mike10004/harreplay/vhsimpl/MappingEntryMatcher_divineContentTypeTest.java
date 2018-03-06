package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MappingEntryMatcher_divineContentTypeTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final TestCase testCase;

    public MappingEntryMatcher_divineContentTypeTest(TestCase testCase) {
        this.testCase = testCase;
    }

    private static final Charset TEST_RESOURCE_CHARSET = StandardCharsets.UTF_8;

    @Parameters
    public static List<TestCase> testCases() throws URISyntaxException, IOException {
        String[] charsetNames = {
                StandardCharsets.UTF_8.name(),
                StandardCharsets.ISO_8859_1.name(),
        };
        File[] textFiles = {
                new File(MappingEntryMatcherTest.class.getResource("/Latin-Lipsum.txt").toURI()),
                new File(MappingEntryMatcherTest.class.getResource("/Russian-Lipsum.txt").toURI()),
                new File(MappingEntryMatcherTest.class.getResource("/Chinese-Lipsum.txt").toURI()),
        };
        List<TestCase> testCases = new ArrayList<>();
        MediaType plainText = MediaType.PLAIN_TEXT_UTF_8.withoutParameters();
        for (File textFile : textFiles) {
            for (String charsetName : charsetNames) {
                try {
                    Charset charset = Charset.forName(charsetName);
                    ByteSource bs = Files.asCharSource(textFile, TEST_RESOURCE_CHARSET).asByteSource(charset);
                    testCases.add(new TestCase(bs, ".txt", plainText));
                } catch (UnsupportedCharsetException ignore) {
                }
            }
        }
        checkState(!testCases.isEmpty());
        testCases.add(new TestCase(ByteSource.wrap(new byte[]{1, 2, 3, 4}), ".dat", MediaType.OCTET_STREAM));
        testCases.add(new TestCase(ByteSource.wrap(new byte[]{1, 2, 3, 4}), ".tmp", MediaType.OCTET_STREAM));
        URL catJpgResource = MappingEntryMatcherTest.class.getResource("/cat.jpg");
        testCases.add(new TestCase(Resources.asByteSource(catJpgResource), ".jpg", MediaType.JPEG));
        return testCases;
    }

    @Test
    public void divineContentType() throws Exception {
        File txtFile = File.createTempFile("something", testCase.filenameSuffix, temporaryFolder.getRoot());
        testCase.data.copyTo(Files.asByteSink(txtFile));
        MappingEntryMatcher m = new MappingEntryMatcher(ImmutableList.of(), temporaryFolder.getRoot().toPath());
        MediaType contentType = m.divineContentType(txtFile);
        assertTrue(String.format("file %s expect %s in range %s", txtFile, contentType, testCase.expectedRange), contentType.is(testCase.expectedRange));
    }

    private static class TestCase {
        public final ByteSource data;
        public final String filenameSuffix;
        public final MediaType expectedRange;

        private TestCase(ByteSource data, String filenameSuffix, MediaType expectedRange) {
            this.data = data;
            this.filenameSuffix = filenameSuffix;
            this.expectedRange = expectedRange;
        }
    }
}

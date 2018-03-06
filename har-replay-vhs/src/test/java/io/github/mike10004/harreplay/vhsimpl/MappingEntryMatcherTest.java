package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.*;

public class MappingEntryMatcherTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void constructHeaders() throws Exception {
        MappingEntryMatcher m = new MappingEntryMatcher(ImmutableList.of(), temporaryFolder.getRoot().toPath());
        Random random = new Random(MappingEntryMatcherTest.class.getName().hashCode());
        int length = 1024;
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        File file = temporaryFolder.newFile();
        Multimap<String, String> rawHeaders = m.constructHeaders(file, MediaType.OCTET_STREAM);
        NameValuePairList.StringMapEntryList headers = NameValuePairList.StringMapEntryList.caseInsensitive(rawHeaders.entries());
        assertEquals("num content-length headers", 1, headers.streamValues(HttpHeaders.CONTENT_LENGTH).count());
        assertEquals("num content-type headers", 1, headers.streamValues(HttpHeaders.CONTENT_TYPE).count());
    }

}
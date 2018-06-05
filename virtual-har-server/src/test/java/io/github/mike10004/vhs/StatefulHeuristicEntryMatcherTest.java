package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.bmp.BmpResponseManufacturer;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig;
import io.github.mike10004.vhs.bmp.BrowsermobVirtualHarServer;
import io.github.mike10004.vhs.bmp.HarReplayManufacturer;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class StatefulHeuristicEntryMatcherTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stateful() throws Exception {
        EntryMatcherFactory<ReplaySessionState> factory = StatefulHeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        List<String> responses = testEntryMatcher(factory);
        assertEquals("responses", Arrays.asList("first", "second", "first"), responses);
    }

    @Test
    public void stateless() throws Exception {
        EntryMatcherFactory<ReplaySessionState> factory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        List<String> responses = testEntryMatcher(factory);
        assertEquals("responses", Arrays.asList("first", "first", "first"), responses);
    }

    private List<String> testEntryMatcher(EntryMatcherFactory<? super ReplaySessionState> entryMatcherFactory) throws Exception {
        File harFile = temporaryFolder.newFile();
        Resources.asByteSource(getClass().getResource("/multiple-requests-same-url.json")).copyTo(Files.asByteSink(harFile));
        List<HarEntry> harEntries = new HarReader().readFromFile(harFile).getLog().getEntries();
        EntryMatcher<? super ReplaySessionState> entryMatcher = entryMatcherFactory
                .createEntryMatcher(harEntries, new HarBridgeEntryParser<>(new SstoehrHarBridge(), HarResponseEncoderFactory.alwaysIdentityEncoding()));
        BmpResponseManufacturer manufacturer = new HarReplayManufacturer(entryMatcher, ImmutableList.of(), ReplaySessionState::countingUrlMethodPairs);
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder(manufacturer).build();
        BrowsermobVirtualHarServer vhs = new BrowsermobVirtualHarServer(config);
        List<String> responses = new ArrayList<>();
        try (VirtualHarServerControl ctrl = vhs.start()) {
            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("localhost", ctrl.getSocketAddress().getPort()));
            for (int i = 0; i < 3; i++) {
                URL url = new URL("http://www.example.com/post");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                conn.setRequestMethod("POST");
                conn.connect();
                try (InputStream in = conn.getInputStream()) {
                    String content = new String(ByteStreams.toByteArray(in), UTF_8);
                    responses.add(content);
                } finally {
                    conn.disconnect();
                }
            }
        }
        return responses;
    }
}
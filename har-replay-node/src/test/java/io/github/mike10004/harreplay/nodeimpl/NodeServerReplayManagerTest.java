package io.github.mike10004.harreplay.nodeimpl;

import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.tests.ImmutableHttpResponse;
import io.github.mike10004.harreplay.tests.ReplayManagerTestBase;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;
import io.github.mike10004.harreplay.tests.Tests;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NodeServerReplayManagerTest extends ReplayManagerTestBase {

    static final String SYSPROP_NODE_EXECUTABLE = "har-replay.node.executable";
    static final String SYSPROP_SERVER_REPLAY_HTTP_PORT = "server-replay.port"; // see pom.xml build-helper-plugin

    @Override
    protected ReplayManagerTester createTester(Path tempDir, File harFile, ReplayServerConfig config) {
        return new NodeServerReplayManagerTester(tempDir, harFile, config);
    }

    @Override
    protected String getReservedPortSystemPropertyName() {
        return SYSPROP_SERVER_REPLAY_HTTP_PORT;
    }

    @Test
    public void getStdoutLogFile() throws Exception {
        MyLogTailerListener listener = new MyLogTailerListener();
        NodeServerReplayManagerConfig config = NodeServerReplayManagerConfig.builder()
                .addStdoutListener(config_ -> listener)
                .build();
        NodeServerReplayManager manager = new NodeServerReplayManager(config);
        File harFile = fixturesRule.getFixtures().http().harFile();
        ReplaySessionConfig sessionConfig = ReplaySessionConfig.builder(temporaryFolder.getRoot().toPath())
                .build(harFile);
        try (ReplaySessionControl ctrl = manager.start(sessionConfig)) {
            HostAndPort proxyAddress = ctrl.getSocketAddress();
            ImmutableHttpResponse rsp1 = Tests.fetch(proxyAddress, URI.create("http://www.example.com/"));
            assertEquals("expect OK", 200, rsp1.status);
            ImmutableHttpResponse rsp2 = Tests.fetch(proxyAddress, URI.create("http://www.somewhere-else.com/"));
            assertEquals("expect not found", 404, rsp2.status);
        }
        boolean tailerStopped = listener.awaitStoppage(5, TimeUnit.SECONDS);
        assertTrue("tailer stopped", tailerStopped);
        assertEquals("num tailed files", 1, listener.stoppedFiles.size());
        List<String> stdoutLines = Files.asCharSource(listener.stoppedFiles.iterator().next(), StandardCharsets.UTF_8).readLines();
        List<String> logLines = stdoutLines.stream().filter(line -> line.matches("^-?\\d{3}.*")).collect(Collectors.toList());
        assertEquals("interaction log lines", 2, logLines.size());
        InteractionLogParser logParser = new InteractionLogParser();
        InteractionLogParser.Interaction interaction1 = logParser.parseInteraction(logLines.get(0));
        assertEquals("status", 200, interaction1.status);
        InteractionLogParser.Interaction interaction2 = logParser.parseInteraction(logLines.get(1));
        assertEquals("status", 404, interaction2.status);
    }

    private static class MyLogTailerListener extends TailerListenerAdapter implements NodeServerReplayManagerConfig.LogTailerListener {

        public final List<String> lines;
        public final List<File> stoppedFiles;
        private final CountDownLatch stoppageLatch;

        public MyLogTailerListener() {
            lines = Collections.synchronizedList(new ArrayList<>());
            stoppedFiles = Collections.synchronizedList(new ArrayList<>());
            stoppageLatch = new CountDownLatch(1);
        }

        @Override
        public void tailerStopped(File file) {
            stoppedFiles.add(file);
            stoppageLatch.countDown();
        }

        public boolean awaitStoppage(long timeout, TimeUnit unit) throws InterruptedException {
            return stoppageLatch.await(timeout, unit);
        }
    }
}

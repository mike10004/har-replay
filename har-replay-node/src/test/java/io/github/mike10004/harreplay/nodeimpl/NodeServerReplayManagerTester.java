package io.github.mike10004.harreplay.nodeimpl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.tests.ReplayManagerTester;
import org.apache.commons.io.input.Tailer;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class NodeServerReplayManagerTester extends ReplayManagerTester {

    private final ReplayServerConfig config;
    public final Multimap<String, StringWriter> tailerBuckets;
    public final List<Exception> tailerExceptions;

    public NodeServerReplayManagerTester(Path tempDir, File harFile) {
        this(tempDir, harFile, ReplayServerConfig.empty());
    }

    public NodeServerReplayManagerTester(Path tempDir, File harFile, ReplayServerConfig config) {
        super(tempDir, harFile);
        this.config = config;
        tailerBuckets = ArrayListMultimap.create();
        tailerExceptions = new ArrayList<>();
    }

    @Override
    protected ReplayManager createReplayManager() {
        return new NodeServerReplayManager(createReplayManagerConfigBuilder().build());
    }

    @Override
    protected ReplayServerConfig configureReplayModule() {
        return config;
    }

    private NodeServerReplayManagerConfig.LogTailerListener createTailerListener(String bucketName) {
        StringWriter bucket = new StringWriter();
        tailerBuckets.put(bucketName, bucket);
        return new NodeServerReplayManagerConfig.LogTailerListener() {
            @Override
            public void tailerStopped(File file) {
                System.out.format("tailer stopped: %s%n", file);
            }

            @Override
            public void init(Tailer tailer) {
            }

            @Override
            public void fileNotFound() {
            }

            @Override
            public void fileRotated() {
            }

            @Override
            public void handle(String line) {
                bucket.append(line);
            }

            @Override
            public void handle(Exception ex) {
                tailerExceptions.add(ex);
            }
        };
    }

    private NodeServerReplayManagerConfig.TailerFactory createTailerFactory(String bucketName) {
        return new NodeServerReplayManagerConfig.TailerFactory() {
            @Override
            public NodeServerReplayManagerConfig.LogTailerListener createTailer(ReplaySessionConfig sessionConfig) {
                return createTailerListener(bucketName);
            }
        };
    }

    private NodeServerReplayManagerConfig.Builder createReplayManagerConfigBuilder() {
        // NodeServerReplayManagerConfig replayManagerConfig = createReplayManagerConfigBuilder().build();
        NodeServerReplayManagerConfig.Builder builder = NodeServerReplayManagerConfig.builder()
                .addStderrListener(createTailerFactory("stderr"))
                .addStdoutListener(createTailerFactory("stdout"));
        String nodeExecutablePath = System.getProperty(NodeServerReplayManagerTest.SYSPROP_NODE_EXECUTABLE, "");
        if (!nodeExecutablePath.isEmpty()) {
            builder.nodeExecutable(new File(nodeExecutablePath));
        }
        return builder;
    }

}

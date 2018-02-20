package io.github.mike10004.harreplay.exec;

import com.github.mike10004.harreplay.ReplayManager;
import com.github.mike10004.harreplay.ReplayManager.ReplaySessionControl;
import com.github.mike10004.harreplay.ReplayManagerConfig;
import com.github.mike10004.harreplay.ReplaySessionConfig;
import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Uninterruptibles;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.harreplay.exec.HarInfoDumper.SummaryDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.TerseDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.VerboseDumper;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class HarReplayMain {

    private static final Logger log = LoggerFactory.getLogger(HarReplayMain.class);

    static final String OPT_NOTIFY = "notify";
    static final String OPT_SCRATCH_DIR = "scratch-dir";
    static final String OPT_PORT = "port";
    static final String OPT_BROWSER = "browser";
    static final Charset NOTIFY_FILE_CHARSET = StandardCharsets.US_ASCII;

    private final OptionParser parser;
    private final OptionSpec<File> notifySpec;
    private final NonOptionArgumentSpec<File> harFileSpec;
    private final OptionSpec<Integer> portSpec;
    private final OptionSpec<File> scratchDirSpec;
    private final OptionSpec<Void> helpSpec;
    private final OptionSpec<Browser> browserSpec;
    private final OptionSpec<HarDumpStyle> harDumpStyleSpec;

    public HarReplayMain() {
        this(new OptionParser());
    }

    @VisibleForTesting
    HarReplayMain(OptionParser parser) throws UsageException {
        this.parser = requireNonNull(parser, "parser");
        helpSpec = parser.acceptsAll(Arrays.asList("h", "help"), "print help and exit").forHelp();
        harFileSpec = parser.nonOptions("har file").ofType(File.class).describedAs("FILE");
        notifySpec = parser.accepts(OPT_NOTIFY, "notify that server is up by printing listening port to file")
                .withRequiredArg().ofType(File.class)
                .describedAs("FILE");
        portSpec = parser.acceptsAll(Arrays.asList("p", OPT_PORT), "port to listen on")
                .withRequiredArg().ofType(Integer.class)
                .describedAs("PORT");
        scratchDirSpec = parser.acceptsAll(Arrays.asList("d", OPT_SCRATCH_DIR), "scratch directory to use")
                .withRequiredArg().ofType(File.class)
                .describedAs("DIRNAME");
        browserSpec = parser.acceptsAll(Arrays.asList("b", OPT_BROWSER), "launch browser configured for replay server; only 'chrome' is supported")
                .withRequiredArg().ofType(Browser.class)
                .describedAs("BROWSER");
        harDumpStyleSpec = parser.acceptsAll(Arrays.asList("dump-har"), "dump har ('summary' or 'verbose')")
                .withRequiredArg().ofType(HarDumpStyle.class)
                .describedAs("STYLE")
                .defaultsTo(HarDumpStyle.summary);
    }

    protected List<HarEntry> readHarEntries(File harFile) throws IOException, HarReaderException {
        CharSource cleanSource = new SstoehrHarCleaningTransform().transform(Files.asCharSource(harFile, StandardCharsets.UTF_8));
        Har har = new HarReader().readFromString(cleanSource.read(), HarReaderMode.LAX);
        return har.getLog().getEntries();
    }

    int main0(String[] args) throws IOException {
        try {
            OptionSet optionSet = parser.parse(args);
            if (optionSet.has(helpSpec)) {
                parser.printHelpOn(System.out);
                return 0;
            }
            ReplayManagerConfig managerconfig = createReplayManagerConfig(optionSet);
            ReplayManager manager = new ReplayManager(managerconfig);
            try (CloseableWrapper<ReplaySessionConfig> sessionConfigWrapper = createReplaySessionConfig(optionSet)) {
                ReplaySessionConfig sessionConfig = sessionConfigWrapper.getWrapped();
                HostAndPort replayServerAddress = HostAndPort.fromParts("localhost", sessionConfig.port);
                try (ReplaySessionControl ignore = manager.start(sessionConfig);
                     ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
                    maybeNotify(sessionConfig, optionSet.valueOf(notifySpec));
                    HarDumpStyle harDumpStyle = optionSet.valueOf(harDumpStyleSpec);
                    try {
                        harDumpStyle.getDumper().dump(readHarEntries(sessionConfig.harFile), System.out);
                    } catch (HarReaderException e) {
                        System.err.format("har-replay: failed to read from har file: %s%n", e.getMessage());
                    }
                    Browser browser = optionSet.valueOf(browserSpec);
                    if (browser != null) {
                        ProcessMonitor<?, ?> monitor = browser.getSupport()
                                .prepare(sessionConfig.scratchDir)
                                .launch(replayServerAddress, processTracker);
                    }

                    sleepForever();
                }
            }
        } catch (UsageException e) {
            System.err.format("har-replay: %s%n", e.getMessage());
            return 1;
        }
        return 0;
    }

    protected void sleepForever() {
        Uninterruptibles.sleepUninterruptibly(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    protected void maybeNotify(ReplaySessionConfig sessionConfig, @Nullable File notifyFile) throws IOException {
        if (notifyFile != null) {
            Files.asCharSink(notifyFile, NOTIFY_FILE_CHARSET).write(String.valueOf(sessionConfig.port));
        }
    }

    protected ReplayManagerConfig createReplayManagerConfig(OptionSet optionSet) {
        return ReplayManagerConfig.auto();
    }

    protected int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    protected CloseableWrapper<ReplaySessionConfig> createReplaySessionConfig(OptionSet optionSet) throws IOException {
        File scratchDir = optionSet.valueOf(scratchDirSpec);
        List<Runnable> cleanups = new ArrayList<>();
        if (scratchDir == null) {
            Path scratchDirPath = java.nio.file.Files.createTempDirectory("har-replay-temporary");
            cleanups.add(() -> {
                try {
                    FileUtils.forceDelete(scratchDirPath.toFile());
                } catch (IOException e) {
                    if (scratchDirPath.toFile().exists()) {
                        log.warn("failed to delete scratch directory " + scratchDirPath, e);
                    }
                }
            });
            scratchDir = scratchDirPath.toFile();
        }
        Integer port = optionSet.valueOf(portSpec);
        if (port == null) {
            port = findUnusedPort();
        }
        File harFile = optionSet.valueOf(harFileSpec);
        if (harFile == null) {
            throw new UsageException("har file must be specified as positional argument");
        }
        ReplaySessionConfig config = ReplaySessionConfig.builder(scratchDir.toPath())
                .port(port)
                .build(harFile);
        return new CloseableWrapper<ReplaySessionConfig>() {
            @Override
            public ReplaySessionConfig getWrapped() {
                return config;
            }

            @Override
            public void close() {
                cleanups.forEach(Runnable::run);
            }
        };
    }

    @SuppressWarnings("unused")
    private static class UsageException extends RuntimeException {
        public UsageException(String message) {
            super(message);
        }

        public UsageException(String message, Throwable cause) {
            super(message, cause);
        }

        public UsageException(Throwable cause) {
            super(cause);
        }
    }

    public static void main(String[] args) throws Exception {
        int exitCode = new HarReplayMain().main0(args);
        System.exit(exitCode);
    }

    protected interface CloseableWrapper<T> extends Closeable {

        T getWrapped();

    }

    public enum Browser {
        chrome;

        BrowserSupport getSupport() {
            switch (this) {
                case chrome:
                    return new ChromeBrowserSupport();
            }
            throw new IllegalStateException("not handled: " + this);
        }
    }

    public enum HarDumpStyle {
        silent,
        terse,
        summary,
        verbose;

        HarInfoDumper getDumper() {
            switch (this) {
                case silent: return HarInfoDumper.silent();
                case terse: return new TerseDumper();
                case summary: return new SummaryDumper();
                case verbose: return new VerboseDumper();
            }
            throw new IllegalStateException("not handled: " + this);
        }
    }

}

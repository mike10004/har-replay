package io.github.mike10004.harreplay.exec;

import io.github.mike10004.subprocess.ProcessMonitor;
import io.github.mike10004.subprocess.ScopedProcessTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.browserup.harreader.HarReader;
import com.browserup.harreader.HarReaderException;
import com.browserup.harreader.HarReaderMode;
import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.exec.ChromeBrowserSupport.OutputDestination;
import io.github.mike10004.harreplay.exec.HarInfoDumper.SummaryDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.TerseDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.VerboseDumper;
import io.github.mike10004.harreplay.vhsimpl.HarReaderFactory;
import io.github.mike10004.harreplay.vhsimpl.VhsReplayManager;
import io.github.mike10004.harreplay.vhsimpl.VhsReplayManagerConfig;
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
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HarReplayMain {

    private static final Logger log = LoggerFactory.getLogger(HarReplayMain.class);

    static final String OPT_NOTIFY = "notify";
    static final String OPT_SCRATCH_DIR = "scratch-dir";
    static final String OPT_PORT = "port";
    static final String OPT_BROWSER = "browser";
    static final String OPT_BROWSER_ARGS = "browser-args";
    static final String OPT_REPLAY_CONFIG = "config";
    static final String OPT_ECHO_BROWSER_OUTPUT = "echo-browser-output";
    static final String OPT_HAR_READER_BEHAVIOR = "har-reader-behavior";
    static final String OPT_HAR_READER_MODE = "har-reader-mode";
    static final String OPT_PRINT = "print";
    static final String OPT_VERSION = "version";
    static final String OPT_HELP = "help";
    static final String OPT_ONLY_PRINT = "only-print";
    static final String OPT_PRINT_WITH_CONTENT = "content-dir";
    static final Charset NOTIFY_FILE_CHARSET = StandardCharsets.US_ASCII;

    private final OptionParser parser;
    private final OptionSpec<File> notifySpec;
    private final NonOptionArgumentSpec<File> harFileSpec;
    private final OptionSpec<Integer> portSpec;
    private final OptionSpec<File> scratchDirSpec;
    private final OptionSpec<Browser> browserSpec;
    private final OptionSpec<String> browserArgsSpec;
    private final OptionSpec<HarPrintStyle> harDumpStyleSpec;
    private final OptionSpec<File> replayConfigSpec;
    private final OptionSpec<HarReaderBehavior> harReaderBehaviorSpec;
    private final OptionSpec<HarReaderMode> harReaderModeSpec;

    public HarReplayMain() {
        this(new OptionParser());
    }

    @VisibleForTesting
    HarReplayMain(OptionParser parser) throws UsageException {
        this.parser = requireNonNull(parser, "parser");
        parser.formatHelpWith(new CustomHelpFormatter());
        parser.acceptsAll(Arrays.asList("h", OPT_HELP), "print help and exit").forHelp();
        parser.acceptsAll(Arrays.asList("V", OPT_VERSION), "print version and exit");
        parser.acceptsAll(Arrays.asList("t", OPT_ONLY_PRINT), "only print content (do not start server)");
        parser.accepts(OPT_ECHO_BROWSER_OUTPUT, "with --browser, print browser output to console");
        parser.accepts(OPT_PRINT_WITH_CONTENT, "with --print=csv, write request/response content to DIR")
                .withRequiredArg().ofType(File.class).describedAs("DIR");
        harFileSpec = parser.nonOptions("har file").ofType(File.class).describedAs("FILE");
        notifySpec = parser.accepts(OPT_NOTIFY, "notify that server is up by printing listening port to file")
                .withRequiredArg().ofType(File.class);
        portSpec = parser.acceptsAll(Arrays.asList("p", "P", OPT_PORT), "port to listen on")
                .withRequiredArg().ofType(Integer.class)
                .describedAs("PORT");
        scratchDirSpec = parser.acceptsAll(Arrays.asList("d", OPT_SCRATCH_DIR), "scratch directory to use")
                .withRequiredArg().ofType(File.class)
                .describedAs("DIRNAME");
        browserSpec = parser.acceptsAll(Arrays.asList("b", OPT_BROWSER), "launch browser configured for replay server; only 'chrome' is supported")
                .withRequiredArg().ofType(Browser.class)
                .describedAs("BROWSER");
        browserArgsSpec = parser.acceptsAll(Arrays.asList("a", OPT_BROWSER_ARGS), "with --browser, add more arguments to browser command line; use CSV syntax for multiple args")
                .withRequiredArg().ofType(String.class)
                .describedAs("ARGS");
        harDumpStyleSpec = parser.acceptsAll(Collections.singletonList(OPT_PRINT), "print har content (choices: " + HarPrintStyle.describeChoices() + ")")
                .withRequiredArg().ofType(HarPrintStyle.class)
                .describedAs("STYLE")
                .defaultsTo(HarPrintStyle.summary);
        replayConfigSpec = parser.acceptsAll(Arrays.asList("f", OPT_REPLAY_CONFIG), "specify replay config file")
                .withRequiredArg().ofType(File.class);
        harReaderBehaviorSpec = parser.accepts(OPT_HAR_READER_BEHAVIOR, "set har reader behavior (EASIER or STOCK)")
                .withRequiredArg().ofType(HarReaderBehavior.class).defaultsTo(HarReaderBehavior.DEFAULT);
        harReaderModeSpec = parser.accepts(OPT_HAR_READER_MODE, "set har reader mode (STRICT or LAX)")
                .withRequiredArg().ofType(HarReaderMode.class).defaultsTo(HarReaderMode.STRICT);
    }

    private ReplayManager createReplayManager(OptionSet optionSet) {
        HarReaderBehavior behavior = (HarReaderBehavior) optionSet.valueOf(OPT_HAR_READER_BEHAVIOR);
        HarReaderMode mode = (HarReaderMode) optionSet.valueOf(OPT_HAR_READER_MODE);
        VhsReplayManagerConfig.Builder b = VhsReplayManagerConfig.builder()
                .harReaderFactory(behavior.getFactory())
                .harReaderMode(mode);
        VhsReplayManagerConfig vhsConfig = b.build();
        return new VhsReplayManager(vhsConfig);
    }

    protected Har readHarFile(OptionSet options, File harFile) throws IOException, HarReaderException {
        HarReaderBehavior harReaderBehavior = harReaderBehaviorSpec.value(options);
        HarReaderMode harReaderMode = harReaderModeSpec.value(options);
        return readHarFile(harFile, harReaderBehavior, harReaderMode);
    }

    @SuppressWarnings("RedundantThrows")
    protected static Har readHarFile(File harFile, HarReaderBehavior harReaderBehavior, HarReaderMode harReaderMode) throws IOException, HarReaderException {
        HarReaderFactory harReaderFactory = harReaderBehavior.getFactory();
        HarReader harReader = harReaderFactory.createReader();
        return harReader.readFromFile(harFile, harReaderMode);
    }

    protected List<HarEntry> readHarEntries(OptionSet options, File harFile) throws IOException, HarReaderException {
        Har har = readHarFile(options, harFile);
        return har.getLog().getEntries();
    }

    protected Iterable<String> tokenize(@Nullable String value) {
        if (value == null) {
            return ImmutableList.of();
        }
        List<String> args = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new StringReader(value))) {
            List<String[]> rows = reader.readAll();
            rows.forEach(row -> args.addAll(Arrays.asList(row)));
        } catch (IOException e) {
            log.warn("failed to tokenize arguments from " + value, e);
        }
        return args;
    }

    protected void runServer(OptionSet optionSet, ReplaySessionConfig sessionConfig) throws IOException {
        HostAndPort replayServerAddress = HostAndPort.fromParts("localhost", sessionConfig.port);
        ReplayManager manager = createReplayManager(optionSet);
        try (ReplaySessionControl ignore = manager.start(sessionConfig);
             ScopedProcessTracker processTracker = new ProcessTrackerWithShutdownHook(Runtime.getRuntime())) {
            maybeNotify(sessionConfig, optionSet.valueOf(notifySpec));
            Browser browser = optionSet.valueOf(browserSpec);
            if (browser != null) {
                Iterable<String> browserArgs = tokenize(optionSet.valueOf(browserArgsSpec));
                //noinspection unused // TODO: provide an alternate method to initate orderly shutdown using this monitor
                ProcessMonitor<?, ?> monitor = browser.getSupport(optionSet)
                        .prepare(sessionConfig.scratchDir)
                        .launch(replayServerAddress, browserArgs, processTracker);
            }
            sleepForever();
        }
    }

    protected void operate(OptionSet optionSet) throws IOException {
        try (CloseableWrapper<ReplaySessionConfig> sessionConfigWrapper = createReplaySessionConfig(optionSet)) {
            ReplaySessionConfig sessionConfig = sessionConfigWrapper.getWrapped();
            HarPrintStyle harDumpStyle = optionSet.valueOf(harDumpStyleSpec);
            try {
                harDumpStyle.getDumper(optionSet).dump(readHarEntries(optionSet, sessionConfig.harFile), System.out);
            } catch (HarReaderException e) {
                System.err.format("har-replay: failed to read from har file: %s%n", e.getMessage());
            }
            if (optionSet.has(OPT_ONLY_PRINT)) {
                return;
            }
            runServer(optionSet, sessionConfig);
        }

    }

    int main0(String[] args) throws IOException {
        try {
            OptionSet optionSet = parser.parse(args);
            if (optionSet.has(OPT_HELP)) {
                parser.printHelpOn(System.out);
                return 0;
            }
            if (optionSet.has(OPT_VERSION)) {
                printVersion(System.out);
                return 0;
            }
            operate(optionSet);
        } catch (UsageException | joptsimple.OptionException e) {
            System.err.format("har-replay: %s%n", e.getMessage());
            System.err.format("har-replay: use --help to print options%n");
            return 1;
        }
        return 0;
    }

    static Properties loadMavenProperties() {
        Properties p = new Properties();
        URL resource = HarReplayMain.class.getResource("/har-replay-exec/maven.properties");
        if (resource == null) {
            log.info("maven.properties is not present on classpath");
            return p;
        }
        try (InputStream in = resource.openStream()) {
            p.load(in);
        } catch (IOException e) {
            log.warn("failed to read from " + resource, e);
        }
        return p;
    }

    static final String DEFAULT_VERSION = "version_unknown";

    @SuppressWarnings("SameParameterValue")
    protected void printVersion(PrintStream out) {
        Properties p = loadMavenProperties();
        String name = p.getProperty("project.parent.name", "har-replay");
        String version = p.getProperty("project.version", DEFAULT_VERSION);
        URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
        out.format("%s %s (in %s)%n", name, version, location);
    }

    protected void sleepForever() {
        Uninterruptibles.sleepUninterruptibly(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    protected void maybeNotify(ReplaySessionConfig sessionConfig, @Nullable File notifyFile) throws IOException {
        if (notifyFile != null) {
            Files.asCharSink(notifyFile, NOTIFY_FILE_CHARSET).write(String.valueOf(sessionConfig.port));
        }
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
        ReplayServerConfig serverConfig = buildReplayServerConfig(optionSet);
        ReplaySessionConfig config = ReplaySessionConfig.builder(scratchDir.toPath())
                .config(serverConfig)
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

    protected Gson createReplayServerConfigGson() {
        return createDefaultReplayServerConfigGson();
    }

    protected static Gson createDefaultReplayServerConfigGson() {
        return ReplayServerConfig.createSerialist();
    }

    protected ReplayServerConfig buildReplayServerConfig(OptionSet optionSet) throws IOException {
        File replayConfigFile = replayConfigSpec.value(optionSet);
        if (replayConfigFile != null) {
            try (Reader reader = Files.asCharSource(replayConfigFile, StandardCharsets.UTF_8).openStream()) {
                return createReplayServerConfigGson().fromJson(reader, ReplayServerConfig.class);
            }
        } else {
            return ReplayServerConfig.empty();
        }
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

        BrowserSupport getSupport(OptionSet options) {
            switch (this) {
                case chrome:
                    return new ChromeBrowserSupport(
                            options.has(OPT_ECHO_BROWSER_OUTPUT) ? OutputDestination.CONSOLE : OutputDestination.FILES);
            }
            throw new IllegalStateException("not handled: " + this);
        }
    }

    public enum HarReaderBehavior {
        EASIER,
        STOCK;

        public static final HarReaderBehavior DEFAULT = EASIER;

        public HarReaderFactory getFactory() {
            switch (this) {
                case EASIER:
                    return HarReaderFactory.easier();
                case STOCK:
                    return HarReaderFactory.stock();
                default:
                    throw new IllegalStateException("unhandled: " + this);
            }
        }

    }

    public enum HarPrintStyle {
        silent,
        terse,
        summary,
        csv,
        verbose;

        HarInfoDumper getDumper(OptionSet optionSet) {
            switch (this) {
                case silent: return HarInfoDumper.silent();
                case terse: return new TerseDumper();
                case summary: return new SummaryDumper();
                case verbose: return new VerboseDumper();
                case csv: return HarInfoDumper.CsvDumper.makeContentWritingInstance((File) optionSet.valueOf(OPT_PRINT_WITH_CONTENT));
            }
            throw new IllegalStateException("not handled: " + this);
        }

        public static String describeChoices() {
            return String.join(", ", Stream.of(values()).map(c -> String.format("'%s'", c.name())).collect(Collectors.toList()));
        }
    }

    private static class ProcessTrackerWithShutdownHook extends ScopedProcessTracker {

        public ProcessTrackerWithShutdownHook(Runtime runtime) {
            addShutdownHook(runtime);
        }

        private void addShutdownHook(Runtime runtime) {
            runtime.addShutdownHook(new Thread(this::destroyAll));
        }
    }

}

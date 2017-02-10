package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.github.mike10004.nativehelper.ProgramWithOutputResult;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

public class NodeServerReplayTest {

    private static final String SYSPROP_SERVER_REPLAY_PORT = "server-replay.port"; // see pom.xml build-helper-plugin

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static int getServerReplayPort() {
        return Integer.parseInt(checkNotNull(System.getProperty(SYSPROP_SERVER_REPLAY_PORT), "server-replay.port is not set"));
    }

    @Test
    public void startAsync() throws Exception {
        Path tempDir = temporaryFolder.getRoot().toPath();
        doStuff(tempDir);
    }

    public static void main(String[] args) throws Exception {
        File buildDir = new File(System.getProperty("user.dir"), "target");
        Path tempDir = java.nio.file.Files.createTempDirectory(buildDir.toPath(), "NodeServerReplayTest");
        doStuff(tempDir);
    }

    private static void doStuff(Path tempDir) throws Exception {
        Path serverReplayDir = tempDir.resolve("server-replay-code");
        prepareServerReplayDir(serverReplayDir);
        System.out.format("prepared server replay code in %s%n", serverReplayDir);
        ReplayManagerConfig replayManagerConfig = new ReplayManagerConfig(serverReplayDir);
        NodeServerReplay replay = new NodeServerReplay(replayManagerConfig);
        File harFile = new File("/tmp/footprint.har");
        int port = getServerReplayPort();
        HostAndPort proxy = HostAndPort.fromParts("localhost", port);
        ReplaySessionConfig sessionParams = ReplaySessionConfig.builder(tempDir)
                .port(port)
                .build(harFile);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            ListenableFuture<ProgramWithOutputFilesResult> programFuture = replay.startAsync(executorService, sessionParams);
            Futures.addCallback(programFuture, new ProgramInfoFutureCallback());
            ChromeDriverManager.getInstance().setup("2.27");
            ChromeOptions chromeOptions = new ChromeOptions();
            File switcherooCrxFile = File.createTempFile("modified-switcheroo", ".crx", sessionParams.scratchDir.toFile());
            ModifiedSwitcheroo.getExtensionCrxByteSource().copyTo(Files.asByteSink(switcherooCrxFile));
            chromeOptions.addExtensions(switcherooCrxFile);
            chromeOptions.addArguments("--user-data-dir=" + tempDir.resolve("chrome-profile").toFile().getAbsolutePath(),
                    "--proxy-server=" + proxy.toString());
            ChromeDriver driver = new ChromeDriver(chromeOptions);
            try {
                blockUntilInputOrEof("exit"::equalsIgnoreCase);
            } finally {
                driver.quit();
            }
            programFuture.cancel(true);
            if (!programFuture.isDone()) {
                try {
                    programFuture.get(3, TimeUnit.SECONDS);
                } catch (java.util.concurrent.CancellationException ignore) {
                }
            }
            System.out.println("program future has finished");
        } finally {
            if (!executorService.isShutdown()) {
                System.out.println("shutting down");
                executorService.shutdownNow();
                System.out.println("finished shutdown");
            }
        }
    }

    public static void prepareServerReplayDir(Path serverReplayDir) throws IOException {
        serverReplayDir.toFile().mkdirs();
        if (!serverReplayDir.toFile().isDirectory()) {
            throw new IOException("directory does not exist and could not be created: " + serverReplayDir);
        }
        String packageJsonText = "{\n" +
                "  \"name\": \"node-server-replay-auto\",\n" +
                "  \"private\": true,\n" +
                "  \"version\": \"0.0.1\"\n" +
                "}";
        Files.write(packageJsonText, serverReplayDir.resolve("package.json").toFile(), UTF_8);
        ProgramWithOutputStringsResult result = Program.running("npm")
                .from(serverReplayDir.toFile())
                .args("install", "server-replay")
                .outputToStrings()
                .execute();
        if (result.getExitCode() != 0) {
            System.out.println(result.getStdout());
            System.err.println(result.getStderr());
            throw new IllegalStateException("nonzero exit from npm: " + result.getExitCode());
        }
    }

    private static class ProgramInfoFutureCallback implements FutureCallback<ProgramWithOutputResult> {
        @Override
        public void onSuccess(ProgramWithOutputResult result) {
            System.out.println("program finished: " + result);
            if (result.getExitCode() != 0) {
                try {
                    result.getStdout().copyTo(System.out);
                    result.getStderr().copyTo(System.err);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                }
            }
        }

        @Override
        public void onFailure(Throwable t) {
            if (!(t instanceof java.util.concurrent.CancellationException)) {
                t.printStackTrace(System.err);
            } else {
                System.out.println("program was cancelled");
            }
        }
    }

    @SuppressWarnings("Duplicates")
    private static String blockUntilInputOrEof(Predicate<? super String> linePredicate) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()))) {
            String line;
            System.out.print("enter 'exit' to quit: ");
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (linePredicate.test(line)) {
                    return line;
                }
            }
        }
        return null;
    }
}
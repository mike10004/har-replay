package io.github.mike10004.harreplay.exec;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.github.mike10004.xvfbmanager.Poller;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.github.mike10004.xvfbmanager.Poller.StopReason;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.tests.Fixtures;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HarReplayIT {

    private static boolean listedDirectoryAlready = false;

    @ClassRule
    public static FixturesRule fixturesRule = Fixtures.asRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void executeHelp() throws Exception {
        File jarFile = findJarFile();
        Subprocess subprocess = Subprocess.running("java")
                .args("-jar", jarFile.getAbsolutePath())
                .arg("--help")
                .build();
        ProcessResult<String, String> result;
        try (ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
            result = subprocess.launcher(processTracker)
                    .outputStrings(Charset.defaultCharset())
                    .launch().await();
        }
        System.out.format("stdout:%n%s%n", result.content().stdout());
        assertEquals("exit code", 0, result.exitCode());
        assertEquals("stderr", "", result.content().stderr());
    }

    @Test
    public void execute() throws Exception {
        File jarFile = findJarFile();
        Fixture fixture = fixturesRule.getFixtures().http();
        File harFile = fixture.harFile();
        File notifyFile = temporaryFolder.newFile();
        notifyFile.deleteOnExit();
        Subprocess subprocess = Subprocess.running("java")
                .args("-jar", jarFile.getAbsolutePath())
                .args("--" + HarReplayMain.OPT_NOTIFY, notifyFile.getAbsolutePath())
                .args("--" + HarReplayMain.OPT_SCRATCH_DIR, temporaryFolder.getRoot().getAbsolutePath())
                .arg(harFile.getAbsolutePath())
                .build();
        ProcessMonitor<String, String> monitor;
        try (ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
            monitor = subprocess.launcher(processTracker)
                    .outputStrings(Charset.defaultCharset(), null)
                    .launch();
            HostAndPort serverAddress = pollUntilNotified(notifyFile);
            System.out.format("listening on %s%n", serverAddress);
            visitSite(serverAddress, fixture);
            monitor.destructor().sendTermSignal().timeout(100, TimeUnit.MILLISECONDS);
        }
        ProcessResult<String, String> result = monitor.await(0, TimeUnit.MILLISECONDS);
        System.out.println(result.content().stdout());
    }

    private String maybeRead(File file, Charset charset) {
        System.out.format("checking %s (length = %d)%n", file, file.length());
        try {
            return Files.asCharSource(file, charset).read();
        } catch (IOException ignore) {
            return "";
        }
    }

    private HostAndPort pollUntilNotified(File notifyFile) throws InterruptedException {
        PollOutcome<Integer> outcome = new Poller<Integer>() {
            @Override
            protected PollAnswer<Integer> check(int pollAttemptsSoFar) {
                 String contents = maybeRead(notifyFile, HarReplayMain.NOTIFY_FILE_CHARSET);
                 if (!contents.trim().isEmpty()) {
                     return resolve(Integer.parseInt(contents));
                 }
                 return continuePolling();
            }
        }.poll(100, 50);
        checkState(outcome.reason == StopReason.RESOLVED, "not resolved: %s", outcome);
        checkNotNull(outcome.content, "resolved, so content should be non-null");
        return HostAndPort.fromParts("localhost", outcome.content);
    }

    private void visitSite(HostAndPort replayServerAddress, Fixture fixture) throws IOException {
        URL url = fixture.startUrl().toURL();
        Proxy proxy = new java.net.Proxy(Proxy.Type.HTTP, new InetSocketAddress(replayServerAddress.getHost(), replayServerAddress.getPort()));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        String html;
        try {
            try (InputStream in = conn.getInputStream()) {
                byte[] bytes = ByteStreams.toByteArray(in);
                html = new String(bytes, StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
        System.out.format("parsing html from %s...%n%s%n%n", url, StringUtils.abbreviateMiddle(html, "\n[...]\n", 256));
        String actualTitle = Jsoup.parse(html).title();
        assertEquals("title", fixture.title(), actualTitle);
    }

    private File findJarFile() throws FileNotFoundException {
        String artifactId = Tests.getTestProperty("project.artifactId");
        String version = Tests.getTestProperty("project.version");
        String filename = String.format("%s-%s-jar-with-dependencies.jar", artifactId, version);
        File targetDir = new File(Tests.getTestProperty("project.build.directory"));
        File jarFile = new File(targetDir, filename);
        if (!jarFile.isFile()) {
            if (!listedDirectoryAlready) {
                System.out.println("target: " + targetDir);
                FileUtils.listFiles(targetDir, null, false).forEach(System.out::println);
                listedDirectoryAlready = true;
            }
            throw new FileNotFoundException(jarFile.getAbsolutePath());
        }
        return jarFile;
    }
}
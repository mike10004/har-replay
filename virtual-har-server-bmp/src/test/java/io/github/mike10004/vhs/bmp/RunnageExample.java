package io.github.mike10004.vhs.bmp;

import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.net.HostAndPort;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.ScratchDirProvider.Scratch;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.Tests;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class RunnageExample {

    public static void main(String[] args) throws Exception {
        int port = 34888;
        new ServerSocket(port).close();
        try (Scratch scratch = ScratchDirProvider.under(FileUtils.getTempDirectory().toPath()).createScratchDir()) {
            Path scratchDir = scratch.getRoot();
            File chromeUserDataDir = scratchDir.resolve("chrome-user-data").toFile();
            File harFile = Tests.getHttpsExampleHarFile(scratchDir);
            List<HarEntry> entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
            System.out.println("requests contained in HAR:");
            entries.stream().map(HarEntry::getRequest).forEach(request -> {
                System.out.format("  %s %s%n", request.getMethod(), request.getUrl());
            });
            EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
            EntryParser<HarEntry> parser = HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge());
            EntryMatcher entryMatcher = new PrintingEntryMatcher(entryMatcherFactory.createEntryMatcher(entries, parser), System.out);
            HarReplayManufacturer responseManufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList());
            AtomicLong counter = new AtomicLong(0);
            BrowsermobVhsConfig.Builder configBuilder = BrowsermobVhsConfig.builder(responseManufacturer)
                    .port(port)
                    .responseListener(new HeaderAddingFilter("X-Filtered-Response-Count", () -> String.valueOf(counter.incrementAndGet())))
                    .scratchDirProvider(ScratchDirProvider.under(scratchDir));
            BrowsermobVhsConfig config = configBuilder.build();
            VirtualHarServer server = new BrowsermobVirtualHarServer(config);
            BrowserCommandLineBuilder commandLineBuilder = BrowserCommandLineBuilder.chrome();
            try (VirtualHarServerControl ctrl = server.start();
                ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
                System.out.format("proxy started on %s%n", ctrl.getSocketAddress());
                Subprocess browserProcess = commandLineBuilder.build(ctrl.getSocketAddress(), chromeUserDataDir)
                        .build();
                System.out.format("launching %s%n", browserProcess);
                browserProcess.launcher(processTracker)
                        .inheritAllStreams()
                        .launch();
                new CountDownLatch(1).await();
            }
        }
    }

    private interface BrowserCommandLineBuilder {

        Subprocess.Builder build(HostAndPort proxySocketAddress, File userDataDir);

        static BrowserCommandLineBuilder chrome() {
            return ((proxySocketAddress, userDataDir) -> {
                return Subprocess.running("google-chrome")
                        .arg("--no-first-run")
                        .arg("--user-data-dir=" + userDataDir.getAbsolutePath())
                        .arg("--proxy-server=" + proxySocketAddress.toString())
                        .arg("--ignore-certificate-errors")
                        .arg("data:,");
            });
        }
    }

    private static class PrintingEntryMatcher implements EntryMatcher {

        private EntryMatcher delegate;
        private PrintStream out;

        PrintingEntryMatcher(EntryMatcher delegate, PrintStream out) {
            this.delegate = delegate;
            this.out = out;
        }

        @Nullable
        @Override
        public HttpRespondable findTopEntry(ParsedRequest request) {
            HttpRespondable entry = delegate.findTopEntry(request);
            if (entry != null) {
                out.format("%s %s -> %s %s%n", request.method, request.url, entry.getStatus(), entry.previewContentType());
            }
            return entry;
        }

    }
}

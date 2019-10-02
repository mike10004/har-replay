package io.github.mike10004.harreplay.exec;

import io.github.mike10004.subprocess.ProcessMonitor;
import io.github.mike10004.subprocess.ProcessTracker;
import com.google.common.net.HostAndPort;

import java.io.IOException;
import java.nio.file.Path;

interface BrowserSupport {

    interface LaunchableBrowser {
        ProcessMonitor<?, ?> launch(HostAndPort replayServerAddress, Iterable<String> moreArguments, ProcessTracker processTracker);
    }

    LaunchableBrowser prepare(Path scratchDir) throws IOException;
}

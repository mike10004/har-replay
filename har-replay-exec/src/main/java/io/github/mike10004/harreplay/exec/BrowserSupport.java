package io.github.mike10004.harreplay.exec;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.google.common.net.HostAndPort;

import java.io.IOException;
import java.nio.file.Path;

interface BrowserSupport {

    interface LaunchableBrowser {
        ProcessMonitor<?, ?> launch(HostAndPort replayServerAddress, Iterable<String> moreArguments, ProcessTracker processTracker);
    }

    LaunchableBrowser prepare(Path scratchDir) throws IOException;
}

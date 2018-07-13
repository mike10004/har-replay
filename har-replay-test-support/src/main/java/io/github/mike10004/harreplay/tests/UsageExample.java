package io.github.mike10004.harreplay.tests;

import com.google.common.io.ByteStreams;
import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

@SuppressWarnings({"unused", "SameParameterValue"})
public abstract class UsageExample {

    public void execute(File harFile) throws IOException {
        ReplaySessionConfig sessionConfig = ReplaySessionConfig.usingTempDir()
                .build(harFile);
        ReplayManager replayManager = createReplayManager();
        try (ReplaySessionControl sessionControl = replayManager.start(sessionConfig)) {
            System.out.println("proxy is listening at localhost:" + sessionControl.getListeningPort());
            doSomethingWithProxy("localhost", sessionConfig.port);
        }
    }

    protected abstract ReplayManager createReplayManager();

    protected void doSomethingWithProxy(String host, int port) throws IOException {
        System.out.format("do something with proxy on %s:%d%n", host, port);
        URL url = new URL("http://www.example.com/");
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        try {
            System.out.format("HTTP %s %s%n", conn.getResponseCode(), conn.getResponseMessage());
            try (InputStream responseStream = conn.getInputStream()) {
                byte[] data = ByteStreams.toByteArray(responseStream);
                System.out.format("%d bytes in body%n", data.length);
            }
        } finally {
            conn.disconnect();
        }
    }

}

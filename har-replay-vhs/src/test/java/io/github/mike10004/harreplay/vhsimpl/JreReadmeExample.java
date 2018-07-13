package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.harreplay.ReplayManager;
import io.github.mike10004.harreplay.ReplaySessionConfig;
import io.github.mike10004.harreplay.ReplaySessionControl;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/**
 * Example code for the readme.
 */
public class JreReadmeExample {

    public static void main(String[] args) throws Exception {
        File harFile = new File("my-session.har");
        ReplaySessionConfig sessionConfig = ReplaySessionConfig.usingTempDir().build(harFile);
        VhsReplayManagerConfig config = VhsReplayManagerConfig.getDefault();
        ReplayManager replayManager = new VhsReplayManager(config);
        try (ReplaySessionControl sessionControl = replayManager.start(sessionConfig)) {
            URL url = new URL("http://www.example.com/");
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", sessionControl.getListeningPort()));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
            try {
                System.out.format("served from HAR: %s %s %s%n", conn.getResponseCode(), conn.getResponseMessage(), url);
                // do something with the connection...
            } finally {
                conn.disconnect();
            }
        }
    }
}

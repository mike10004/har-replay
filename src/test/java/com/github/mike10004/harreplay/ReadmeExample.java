package com.github.mike10004.harreplay;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Example code for the readme.
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public class ReadmeExample {

    public static void example(File harFile) throws IOException {
        ReplayManagerConfig replayManagerConfig = ReplayManagerConfig.auto();
        ReplayManager replayManager = new ReplayManager(replayManagerConfig);
        ReplaySessionConfig sessionConfig = ReplaySessionConfig.usingTempDir()
                .build(harFile);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> server = replayManager.startAsync(executorService, sessionConfig);
            doSomethingWithProxy("localhost", sessionConfig.port);
            server.cancel(true);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static void doSomethingWithProxy(String host, int port) throws IOException {
        System.out.format("do something with proxy on %s:%d%n", host, port);
        try (CloseableHttpClient client = HttpClients.custom().setProxy(new HttpHost(host, port)).build()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet("http://www.example.com/"))) {
                System.out.println("response: " + response.getStatusLine());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        example(new File(ReadmeExample.class.getResource("/https.www.example.com.har").toURI()));
    }
}

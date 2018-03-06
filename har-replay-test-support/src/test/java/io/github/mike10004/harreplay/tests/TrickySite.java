package io.github.mike10004.harreplay.tests;

import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.base.CharMatcher;
import com.google.common.io.ByteSource;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import org.apache.commons.io.FileUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Program that generates a HAR file capturing a particularly tricky website
 * interaction. There are some websites that serve JavaScript resources as
 * content-type {@code application/octet-stream}, and when the user agent
 * executes the code, it sets {@code window.location} to an HTTPS site.
 *
 */
public class TrickySite {

    // keytool -genkey -keyalg RSA -alias selfsigned \
    //         -keystore keystore.jks \
    //         -storepass password \
    //         -validity 360 \
    //         -keysize 2048 \
    //         -ext SAN=DNS:localhost,IP:127.0.0.1 \
    //         -validity 9999
    private static byte[] createKeystore(String keystorePassword) throws InterruptedException, IOException, TimeoutException {
        File keystoreFile = new File(FileUtils.getTempDirectory(), UUID.randomUUID() + ".jks");
        Subprocess proc = Subprocess.running("keytool")
                .args("-genkey",
                        "-dname", "cn=Mark Jones, ou=Java, o=Oracle, c=US",
                        "-keyalg", "RSA",
                        "-alias", "selfsigned",
                        "-keystore", keystoreFile.getAbsolutePath(),
                        "-storepass", keystorePassword,
                        "-keypass", keystorePassword,
                        "-validity", "360",
                        "-keysize", "2048",
                        "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                        "-validity", "9999")
                .build();
        byte[] keystoreBytes;
        try (ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
            ProcessResult<?, ?> result = proc.launcher(processTracker)
                    .outputStrings(Charset.defaultCharset(), ByteSource.empty())
                    .launch().await(5, TimeUnit.SECONDS);
            if (result.exitCode() != 0) {
                throw new IllegalStateException("nonzero exit code " + result.exitCode());
            }
            keystoreBytes = java.nio.file.Files.readAllBytes(keystoreFile.toPath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            keystoreFile.delete();
        }
        return keystoreBytes;
    }

    public static void main(String[] args) throws Exception {
        int port = 56789;
        AtomicInteger counter = new AtomicInteger(0);
        NanoHTTPD server = new NanoHTTPD(port) {
            @Override
            public Response serve(IHTTPSession session) {
                if (isFaviconRequest(session)) {
                    return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "application/octet-stream", new ByteArrayInputStream(new byte[0]), 0);
                }
                return NanoHTTPD.newFixedLengthResponse(counter.incrementAndGet() + " hello world");
            }

            private boolean isFaviconRequest(IHTTPSession session) {
                return "/favicon.ico".equals(URI.create(session.getUri()).getPath());
            }
        };
        KeyStore keystore = KeyStore.getInstance("JKS");
        String keystorePassword = CharMatcher.anyOf("ABCDEFabcdef0123456789").retainFrom(UUID.randomUUID().toString());
        System.out.format("keystore password: %s%n", keystorePassword);
        byte[] keystoreBytes = createKeystore(keystorePassword);
        System.out.format("keystore generated as %d bytes%n", keystoreBytes.length);
        try (InputStream stream = new ByteArrayInputStream(keystoreBytes)) {
            keystore.load(stream, keystorePassword.toCharArray());
        }
        System.out.format("keystore loaded: %s%n", keystore);
        String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
        keyManagerFactory.init(keystore, keystorePassword.toCharArray());
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(keyManagerFactory.getKeyManagers(), null, null);
        server.makeSecure(sc.getServerSocketFactory(), null);
        System.out.format("starting server...%n");
        server.start();
        try {
            String hostname = Optional.ofNullable(server.getHostname()).orElse("localhost");
            System.out.format("serving at https://%s:%s/%n", hostname, server.getListeningPort());
            new CountDownLatch(1).await();
        } finally {
            server.stop();
        }
    }
}

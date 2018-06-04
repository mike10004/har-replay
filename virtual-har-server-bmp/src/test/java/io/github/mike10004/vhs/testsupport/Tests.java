package io.github.mike10004.vhs.testsupport;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

public class Tests {

    private Tests(){}

    public static File getHttpsExampleHarFile(Path temporaryDirectory) throws IOException {
        ByteSource byteSource = Resources.asByteSource(Tests.class.getResource("/https.www.example.com.har"));
        File harFile = File.createTempFile("https-example", ".har", temporaryDirectory.toFile());
        byteSource.copyTo(Files.asByteSink(harFile));
        return harFile;
    }

    public static File getReplayTest1HarFile(Path temporaryDirectory) throws IOException {
        ByteSource byteSource = Resources.asByteSource(Tests.class.getResource("/replay-test-1.har"));
        File harFile = File.createTempFile("replay-test-1-", ".har", temporaryDirectory.toFile());
        byteSource.copyTo(Files.asByteSink(harFile));
        return harFile;
    }

    public static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static boolean isStillAlive(HostAndPort socketAddress) {
        try (Socket ignore = new Socket(socketAddress.getHost(), socketAddress.getPort())) {
            return true;
        } catch (IOException e) {
            System.err.format("test of socket liveness failed: %s%n", e.toString());
            return false;
        }
    }

    public static void configureClientToTrustBlindly(HttpClientBuilder clientBuilder) throws GeneralSecurityException {
        SSLContext sslContext = SSLContexts
                .custom()
                .loadTrustMaterial(blindTrustStrategy())
                .build();
        clientBuilder.setSSLContext(sslContext);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, blindHostnameVerifier());
        clientBuilder.setSSLSocketFactory(sslsf);
        clientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }

    public static javax.net.ssl.HostnameVerifier blindHostnameVerifier() {
        return new BlindHostnameVerifier();
    }

    public static TrustStrategy blindTrustStrategy() {
        return new BlindTrustStrategy();
    }

    private static final class BlindHostnameVerifier implements javax.net.ssl.HostnameVerifier {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

    private static final class BlindTrustStrategy implements TrustStrategy {
        @Override
        public boolean isTrusted(java.security.cert.X509Certificate[] chain, String authType)  {
            return true;
        }

    }

    public static CloseableHttpClient buildBlindlyTrustingHttpClient(HostAndPort proxy) throws GeneralSecurityException {
        HttpClientBuilder b = HttpClients.custom()
                .useSystemProperties();
        if (proxy != null) {
            b.setProxy(new HttpHost(proxy.getHost(), proxy.getPort()));
        }
        b.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
        configureClientToTrustBlindly(b);
        return b.build();
    }

    public static File copyImageForUpload(Path directory) throws IOException {
        return copyFileFromClasspath("/image-for-upload.jpg", "image-for-upload", ".jpeg", directory);
    }

    @SuppressWarnings("SameParameterValue")
    static File copyFileFromClasspath(String resourcePath, String prefix, String suffix, Path tempdir) throws IOException {
        URL resource = Tests.class.getResource(resourcePath);
        if (resource == null) {
            throw new FileNotFoundException(resourcePath);
        }
        File file = File.createTempFile(prefix, suffix, tempdir.toFile());
        Resources.asByteSource(resource).copyTo(Files.asByteSink(file));
        file.deleteOnExit();
        return file;
    }

}

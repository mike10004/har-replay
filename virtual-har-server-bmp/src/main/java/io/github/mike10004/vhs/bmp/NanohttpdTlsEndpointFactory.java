package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.TlsEndpointFactory;
import io.github.mike10004.vhs.bmp.repackaged.fi.iki.elonen.NanoHTTPD;
import net.lightbody.bmp.mitm.TrustSource;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import static java.util.Objects.requireNonNull;

/**
 * Factory that produces a TLS endpoint that accepts TLS connections
 * as a normal web server would.
 */
public class NanohttpdTlsEndpointFactory implements TlsEndpointFactory {

    private SSLServerSocketFactory socketFactory;
    private TrustSource trustSource;
    @Nullable
    private Integer port;

    public NanohttpdTlsEndpointFactory(SSLServerSocketFactory socketFactory, TrustSource trustSource, @Nullable Integer port) {
        this.socketFactory = requireNonNull(socketFactory, "socketFactory");
        this.trustSource = requireNonNull(trustSource, "trustSource");
        this.port = port;
    }

    @Override
    public TlsEndpoint produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException {
        return new NanoEndpoint(findPort());
    }

    private int findPort() throws IOException {
        if (port != null) {
            return port.intValue();
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Creates a factory that constructs an instance with SSL server socket factory
     * and trust source generated from the same keystore data.
     * @param keystoreData the keystore data
     * @param port the port
     * @return the factory
     * @throws IOException on I/O error
     * @throws GeneralSecurityException on security error
     */
    public static NanohttpdTlsEndpointFactory create(KeystoreData keystoreData, @Nullable Integer port) throws IOException, GeneralSecurityException {
        SSLServerSocketFactory sslServerSocketFactory = createSSLServerSocketFactory(keystoreData);
        TrustSource trustSource = createTrustSource(keystoreData);
        return new NanohttpdTlsEndpointFactory(sslServerSocketFactory, trustSource, port);
    }

    public static SSLServerSocketFactory createSSLServerSocketFactory(KeystoreData keystoreData) throws IOException, GeneralSecurityException {
        KeyStore keystore = keystoreData.loadKeystore();
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keystore, keystoreData.keystorePassword);
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(keyManagerFactory.getKeyManagers(), null, null);
        return sc.getServerSocketFactory();
    }

    @SuppressWarnings("RedundantThrows")
    public static TrustSource createTrustSource(KeystoreData keystoreData) throws IOException, GeneralSecurityException {
        return TrustSource.defaultTrustSource()
                .add(keystoreData.asCertificateAndKeySource().load().getCertificate());
    }

    private class NanoServer extends NanoHTTPD {

        public NanoServer(int port) {
            super(port);
        }

    }

    protected NanoHTTPD createServer(int port) {
        NanoHTTPD server = new NanoServer(port);
        server.makeSecure(socketFactory, null);
        return server;
    }

    private class NanoEndpoint implements TlsEndpoint {

        private NanoHTTPD server;
        private HostAndPort socketAddress;

        public NanoEndpoint(int port) throws IOException {
            server = createServer(port);
            server.start();
            socketAddress = HostAndPort.fromParts("localhost", server.getListeningPort());
        }

        @Override
        public HostAndPort getSocketAddress() {
            return socketAddress;
        }

        @SuppressWarnings("RedundantThrows")
        @Override
        public void close() throws IOException {
            server.stop();
        }

        @Nullable
        @Override
        public TrustSource getTrustSource() {
            return trustSource;
        }
    }

}

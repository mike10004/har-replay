package io.github.mike10004.harreplay.tests;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
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

    private static final String redirectingScriptPath = "/this-is-the-redirecting-script";
    private static final String START_PAGE_PATH = "/start.html";
    private static final String FAVICON_PATH = "/favicon.ico";

    private static class Server extends NanoHTTPD {

        private AtomicInteger counter;
        private URI baseUrl;
        private byte[] faviconBytes;
        private static final String redirectingScriptContentType = "application/octet-stream";

        public Server(int port) throws IOException {
            super(port);
            counter = new AtomicInteger(0);
            faviconBytes = Resources.toByteArray(TrickySite.class.getResource("/nps-favicon.ico"));
            baseUrl = URI.create(String.format("https://%s:%s/", "localhost", port));
        }

        @Override
        public Response serve(IHTTPSession session) {
            try {
                URI url = URI.create(session.getUri());
                if (isPath(url, FAVICON_PATH)) {
                    return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/x-icon", new ByteArrayInputStream(faviconBytes), faviconBytes.length);
                }
                if (isPath(url, START_PAGE_PATH)) {
                    return createStartPage(baseUrl);
                }
                if (isPath(url, redirectingScriptPath)) {
                    return createRedirectingScript(baseUrl);
                }
                if (isPath(url, OTHER_PAGE_PATH)) {
                    return createRedirectDestinationPage(baseUrl);
                }
                if (isPath(url, "/")) {
                    return createPageWithTrickyRedirect(baseUrl, redirectingScriptPath);
                }
                System.err.format("returning 404 for %s %s%n", session.getMethod(), session.getUri());
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not Found: " + url.getPath());
            } catch (Exception e) {
                LoggerFactory.getLogger(TrickySite.class).error("failure to serve", e);
                return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.toString());
            }
        }

        private boolean isPath(URI url, String path) {
            return path.equals(url.getPath());
        }

        public URI getStartUrl() throws URISyntaxException {
            return new URIBuilder(baseUrl).setPath("/start.html").build();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 56789;
        Server server = new Server(port);
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
        Path tempDir = java.nio.file.Files.createTempDirectory("tricky-site-profile");
        System.out.format("starting server...%n");
        server.start();
        try (ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
            URI startUrl = server.getStartUrl();
            System.out.format("serving at %s%n", startUrl);
            Subprocess proc = BrowserCommandLineBuilder.chrome(startUrl)
                    .build(null, tempDir.toFile());
            System.out.format("executing %s%n", proc);
            ProcessMonitor<?, ?> monitor = proc.launcher(processTracker)
                    .inheritAllStreams()
                    .launch();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (monitor.process().isAlive()) {
                    monitor.destructor().sendTermSignal().kill();
                }
            }));
            new CountDownLatch(1).await();
        } finally {
            server.stop();
            try {
                FileUtils.forceDelete(tempDir.toFile());
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private static final freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_27);

    static {
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    private static final AtomicInteger templateCounter = new AtomicInteger(0);

    private static String render(String templateSource, Map<String, Object> dataModel) throws IOException, TemplateException {
        String name = "template" + templateCounter.incrementAndGet();
        Template template = new Template(name, templateSource, cfg);
        StringWriter out = new StringWriter(templateSource.length() + 1024);
        template.process(dataModel, out);
        out.flush();
        return out.toString();
    }

    private static NanoHTTPD.Response createStartPage(URI baseUrl) throws IOException, TemplateException {
        String htmlTemplate = String.format("<!DOCTYPE html>\n" +
                "<html>\n" +
                "  <body>\n" +
                "    <a href=\"${baseUrl}\" title=\"Base URL\">${baseUrl}</a>\n" +
                "  </body>\n" +
                "</html>\n", baseUrl, baseUrl);
        String rendered = render(htmlTemplate, ImmutableMap.of("baseUrl", baseUrl.toString()));
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html", rendered);
    }

    private interface BrowserCommandLineBuilder {

        Subprocess build(HostAndPort proxySocketAddress, File userDataDir);

        static BrowserCommandLineBuilder chrome(URI startUrl) {
            return ((proxySocketAddress, userDataDir) -> {
                Subprocess.Builder b = Subprocess.running("google-chrome")
                        .arg("--no-first-run")
                        .arg("--user-data-dir=" + userDataDir.getAbsolutePath())
                        .arg("--allow-insecure-localhost");
                if (proxySocketAddress != null) {
                    b.arg("--proxy-server=" + proxySocketAddress.toString());
                }
                b.arg(startUrl.toString());
                return b.build();
            });
        }
    }

    private static NanoHTTPD.Response createPageWithTrickyRedirect(URI baseUrl, String scriptPath) throws IOException, TemplateException {
//        URL resourcePage = TrickySite.class.getResource("/page-with-tricky-redirect.html");
        // during development, this version is more up to date
        URL resourcePage = new File("har-replay-test-support/src/test/resources/page-with-tricky-redirect.html").toURI().toURL();
        String templateHtml = Resources.toString(resourcePage, StandardCharsets.UTF_8);
        Map<String, Object> model = ImmutableMap.<String, Object>builder()
                .put("baseUrl", baseUrl)
                .put("scriptPath", scriptPath)
                .build();
        String renderedPage = render(templateHtml, model);
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html", renderedPage);
    }

    private static NanoHTTPD.Response createRedirectingScript(URI baseUrl) throws IOException, TemplateException {
        String template = "window.setTimeout(function() {\n" +
                "  console.info('setting window.location to /other.html');\n" +
                "  window.location = \"${baseUrl}other.html\";\n" +
                "}, 1);\n";
        String rendered = render(template, ImmutableMap.of("baseUrl", baseUrl.toString()));
        return NanoHTTPD.newFixedLengthResponse(Status.OK, Server.redirectingScriptContentType, rendered);
    }

    private static final String OTHER_PAGE_TEXT = "This is the redirect destination";
    private static final String OTHER_PAGE_PATH = "/other.html";

    private static NanoHTTPD.Response createRedirectDestinationPage(URI baseUrl) {
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/plain", OTHER_PAGE_TEXT);
    }
}

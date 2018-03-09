package io.github.mike10004.harreplay.tests;

import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.github.mike10004.seleniumhelp.AutoCertificateAndKeySource;
import com.github.mike10004.seleniumhelp.ChromeWebDriverFactory;
import com.github.mike10004.seleniumhelp.TrafficCollector;
import com.github.mike10004.seleniumhelp.TrafficGenerator;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderMode;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.github.mike10004.harreplay.tests.Fixtures.JavascriptRedirectInfo;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.utils.URIBuilder;
import org.littleshoot.proxy.MitmManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

/**
 * Program that generates a HAR file capturing a particularly tricky website
 * interaction. There are some websites that serve JavaScript resources as
 * content-type {@code application/octet-stream}, and when the user agent
 * executes the code, it sets {@code window.location} to an HTTPS site.
 *
 */
public class TrickySite {

    private final Server server;

    public TrickySite(Server server) {
        this.server = server;
    }

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

    private static final MediaType FAVICON_CONTENT_TYPE = MediaType.PNG;
    static final String FAVICON_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAABGdBTUEAALGPC/xhBQAAACBj" +
            "SFJNAAB6JgAAgIQAAPoAAACA6AAAdTAAAOpgAAA6mAAAF3CculE8AAABiVBMVEX///8AAACo" +
            "azyyckC3dkK6d0O3dkK5d0O1dEGyckCubz6yckC6d0O5dkO0dEG1dEGzc0C6d0O6d0O7eETC" +
            "i164dkK5d0O2dUK3dUKyckDHiVnUq4vs39XVsJOxcT+pbDywd0NObEbAtJ7k0cHFgU2sbj2t" +
            "dUiBa0BEYDZxZDiSZzmzf1aiaTqOZzlFYDZPYTdBYDadaDpmYzhVa0N5eFOhaTqIZjlhYzdH" +
            "aEF3j3OnlHWAZjlJYDd+ZTlwZDhzZDiPZzlgYjdyZDieaDqBZjlsZDh1ZDh+ZTnGf0jBfkfE" +
            "fkeRc0FkaDvs08Dny7bq0b3mxq7ivaGadUJOYziIcUDr07/v28vt1sXx39Hu2cns08FEYTc+" +
            "YDaMcUDjv6T16uH16uDoy7a0e0ZPYzhCYDdrajzpzbf69fD9+ff69O/68+7r0b2ueUVDYTdj" +
            "aDthaDtDZT3fyrnGoYTj0cOlajyjaTqrdUo8XzZLa0U9YDdDYDaisp7////q7eluiGpzjG+s" +
            "u6mVqJJog2Ry57GGAAAASHRSTlMAABKHvN3g1Z2Kh73qzrqCWOHpzOW+0Iy0iPbj9eWAJfz9" +
            "+O39aszQ2dPz0anb3dvo/tPh1XYu4v3er3v9ZcG1POfXEUnRzhkWwcmoAAAAAWJLR0QAiAUd" +
            "SAAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB+IDBw0lAdoDIWIAAADISURBVBjTY2Bg" +
            "YGRkYmZhZWPnYGJkZAABRkZODzDgggpw8/B6gvhefPwCYAFBD28fD18/f/+AQCGwgLBHUHBI" +
            "aFh4RGSUCFhA1CM6JtYjLj4h0UMMLCCelJySmpaekZmVLQEWkMzJTc6TkpaR9fCQAwvIK+Qr" +
            "FigVFhUrq6hC3KGmrqGpVVJaUqYNdZiOrp6+gaGRsYkpVMDMvLy8otLC0grudOuq6praOhtb" +
            "uACjXX1DY5M9I0LAwbG83MkZSYDRxdXNnREsAABtnCi/4fTAigAAACV0RVh0ZGF0ZTpjcmVh" +
            "dGUAMjAxOC0wMy0wN1QxMzozNzowMS0wNTowMIGe70gAAAAldEVYdGRhdGU6bW9kaWZ5ADIw" +
            "MTgtMDMtMDdUMTM6Mzc6MDEtMDU6MDDww1f0AAAAAElFTkSuQmCC";

    static byte[] decodeFavicon() {
        return BaseEncoding.base64().decode(FAVICON_BASE64);
    }

    private static class Server extends NanoHTTPD {

        private static final String HEADER_NAME = "X-Tricky-Site-Response-Count";

        private final AtomicInteger counter;
        private final Map<String, Object> model;
        private byte[] faviconBytes;
        private final AtomicInteger templateCounter = new AtomicInteger(0);
        private static final String REDIRECTING_SCRIPT_CONTENT_TYPE = "application/octet-stream";
        private final freemarker.template.Configuration cfg;
        private final URI baseUrl;

        @SuppressWarnings("RedundantThrows")
        public Server(int port) throws IOException, URISyntaxException {
            super(port);
            counter = new AtomicInteger(0);
            faviconBytes = decodeFavicon();
            baseUrl = URI.create(String.format("https://%s:%s/", "localhost", port));
            model = new HashMap<>(buildModelBase(baseUrl));
            cfg = createFreemarkerConfig();
        }

        private static ImmutableMap<String, Object> buildModelBase(URI baseUrl) throws URISyntaxException {
            URI redirectDest = new URIBuilder(baseUrl).setPath(JavascriptRedirectInfo.OTHER_PAGE_PATH).build();
            return ImmutableMap.<String, Object>builder()
                    .put("baseUrl", baseUrl.toString())
                    .put("redirectDestination", redirectDest.toString())
                    .put("redirectingScriptPath", JavascriptRedirectInfo.REDIRECTING_SCRIPT_PATH)
                    .build();
        }

        @Override
        public Response serve(IHTTPSession session) {
            Response headerlessResponse = serveWithoutHeader(session);
            headerlessResponse.addHeader(HEADER_NAME, String.valueOf(counter.incrementAndGet()));
            return headerlessResponse;
        }

        private Response serveWithoutHeader(IHTTPSession session) {
            try {
                URI url = URI.create(session.getUri());
                if (isPath(url, JavascriptRedirectInfo.FAVICON_PATH)) {
                    return createFavicon();
                }
                if (isPath(url, JavascriptRedirectInfo.START_PAGE_PATH)) {
                    return createStartPage();
                }
                if (isPath(url, JavascriptRedirectInfo.REDIRECTING_SCRIPT_PATH)) {
                    return createRedirectingScript();
                }
                if (isPath(url, JavascriptRedirectInfo.OTHER_PAGE_PATH)) {
                    return createRedirectDestinationPage();
                }
                if (isPath(url, "/")) {
                    return createPageWithTrickyRedirect();
                }
                System.err.format("returning 404 for %s %s%n", session.getMethod(), session.getUri());
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not Found: " + url.getPath());
            } catch (Exception e) {
                LoggerFactory.getLogger(TrickySite.class).error("failure to serve", e);
                return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.toString());
            }
        }

        private NanoHTTPD.Response createFavicon() {
            return NanoHTTPD.newFixedLengthResponse(Status.OK, FAVICON_CONTENT_TYPE.toString(), new ByteArrayInputStream(faviconBytes), faviconBytes.length);
        }

        private NanoHTTPD.Response createStartPage() throws IOException, TemplateException {
            String htmlTemplate = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "  <body>\n" +
                    "    <a href=\"${baseUrl}\" title=\"Base URL\">${baseUrl}</a>\n" +
                    "  </body>\n" +
                    "</html>\n";
            String rendered = render(htmlTemplate, model);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html", rendered);
        }

        private boolean isPath(URI url, String path) {
            return path.equals(url.getPath());
        }

        public URI getFinalUrl() {
            try {
                return new URIBuilder(baseUrl).setPath(JavascriptRedirectInfo.OTHER_PAGE_PATH).build();
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        public URI getStartUrl() {
            return baseUrl;
        }

        private NanoHTTPD.Response createRedirectDestinationPage() {
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/plain", JavascriptRedirectInfo.OTHER_PAGE_TEXT);
        }

        private NanoHTTPD.Response createPageWithTrickyRedirect() throws IOException, TemplateException {
            String templateHtml = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <script src=\"${redirectingScriptPath}\"></script>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    This is the page that loads a script that performs a redirect\n" +
                    "</body>\n" +
                    "</html>\n";
            String renderedPage = render(templateHtml, model);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html", renderedPage);
        }

        private NanoHTTPD.Response createRedirectingScript() throws IOException, TemplateException {
            String template = "window.setTimeout(function() {\n" +
                    "  console.info('setting window.location to ${redirectDestination}');\n" +
                    "  window.location = \"${redirectDestination}\";\n" +
                    "}, 1);\n";
            String rendered = render(template, model);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, REDIRECTING_SCRIPT_CONTENT_TYPE, rendered);
        }

        static freemarker.template.Configuration createFreemarkerConfig() {
            freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_27);
            cfg.setDefaultEncoding("UTF-8");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setLogTemplateExceptions(false);
            cfg.setWrapUncheckedExceptions(true);
            return cfg;
        }

        private String render(String templateSource, Map<String, Object> dataModel) throws IOException, TemplateException {
            String name = "template" + templateCounter.incrementAndGet();
            Template template = new Template(name, templateSource, cfg);
            StringWriter out = new StringWriter(templateSource.length() + 1024);
            template.process(dataModel, out);
            out.flush();
            return out.toString();
        }

    }

    private static class TrustingTrafficCollector extends TrafficCollector {

        public TrustingTrafficCollector(TrafficCollector.Builder builder) {
            super(builder);

        }

        @Override
        protected MitmManager createMitmManager(BrowserMobProxy proxy, CertificateAndKeySource certificateAndKeySource) {
            MitmManager mitmManager = ImpersonatingMitmManager.builder()
                    .rootCertificateSource(certificateAndKeySource)
                    .trustAllServers(true)
                    .build();
            return mitmManager;
        }
    }

    private static Supplier<BrowserMobProxy> browsermobInstantiator() {
        return BrowserMobProxyServer::new;
    }

    private interface BrowserCommandLineBuilder {

        Subprocess build(HostAndPort proxySocketAddress);

        static List<String> createArgs(Path userDataDir, URI startUrl, @Nullable HostAndPort proxySocketAddress) {
            List<String> args = new ArrayList<>();
            args.add("--no-first-run");
            args.add("--user-data-dir=" + userDataDir.toFile().getAbsolutePath());
            args.add("--allow-insecure-localhost");
            if (proxySocketAddress != null) {
                args.add("--proxy-server=" + proxySocketAddress.toString());
            }
            args.add(startUrl.toString());
            return args;
        }

        static BrowserCommandLineBuilder chrome(Path userDataDir, URI startUrl) {
            return proxySocketAddress -> {
                Subprocess.Builder b = Subprocess.running("google-chrome")
                        .args(createArgs(userDataDir, startUrl, proxySocketAddress));
                return b.build();
            };
        }
    }

    public void captureHar(File destinationHarFile) throws Exception {
        ChromeDriverSetupRule.doSetup();
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
        XvfbManager xvfbManager = new XvfbManager();
        try (XvfbController xvfbController = xvfbManager.start(tempDir)) {
            URI startUrl = server.getStartUrl();
            ChromeWebDriverFactory factory = ChromeWebDriverFactory.builder()
                    .environment(xvfbController.newEnvironment()).build();
            TrafficCollector collector = new TrustingTrafficCollector(TrafficCollector.builder(factory)
                    .collectHttps(new AutoCertificateAndKeySource(tempDir))
                    .localProxyInstantiator(browsermobInstantiator()));
            net.lightbody.bmp.core.har.Har har = collector.collect(new TrafficGenerator<Void>() {
                @Override
                public Void generate(WebDriver driver) throws IOException {
                    System.out.format("visiting %s%n", startUrl);
                    driver.get(startUrl.toString());
                    new WebDriverWait(driver, 120).until(ExpectedConditions.urlToBe(server.getFinalUrl().toString()));
//                    driver.get("https://www.example.com/");
                    return (Void) null;
                }
            }).har;
            writeHar(har, destinationHarFile);
        } finally {
            server.stop();
            try {
                FileUtils.forceDelete(tempDir.toFile());
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private static void writeHar(net.lightbody.bmp.core.har.Har har, File harFile) throws IOException {
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter().writeValue(harFile, har);
    }

    public static void main(String[] args) throws Exception {
        File harFile = File.createTempFile("javascript-redirect", ".har");
        int port = 56789;
        Server server = new Server(port);
        String serverSocketAddress = HostAndPort.fromParts("localhost", port).toString();
        String replacement = "www.redi123.com";
        checkState(replacement.length() == serverSocketAddress.length(), "replacement length must equal original address length: %s and %s", serverSocketAddress, replacement);
        new TrickySite(server).captureHar(harFile);
        System.out.format("%s created%n", harFile);
        de.sstoehr.harreader.model.Har har = new HarReader().readFromFile(harFile, HarReaderMode.LAX);
        new HarInfoDumper.SummaryDumper().dump(har.getLog().getEntries(), System.out);
        System.out.println();
        Pattern serverSocketAddressPattern = Pattern.compile(Pattern.quote(serverSocketAddress));
        System.out.format("lines matching %s:%n", serverSocketAddressPattern);
        String harAsString = Files.asCharSource(harFile, StandardCharsets.UTF_8).read();
        Grep.byPattern(serverSocketAddressPattern).filter(CharSource.wrap(harAsString))
                .forEach(match -> {
                    System.out.format("%3d %s%n", match.lineNumber(), match.line());
                });
        String remapped = serverSocketAddressPattern.matcher(harAsString).replaceAll(replacement);
        File remappedHarFile = File.createTempFile("javascript-redirect-clean", ".har");
        Files.asCharSink(remappedHarFile, StandardCharsets.UTF_8).write(remapped);
        System.out.format("%s is cleaned har%n", remappedHarFile);
    }

}

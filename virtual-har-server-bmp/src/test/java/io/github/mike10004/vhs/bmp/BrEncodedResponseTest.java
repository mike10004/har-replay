package io.github.mike10004.vhs.bmp;

import com.github.mike10004.xvfbtesting.XvfbRule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarResponse;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.harbridge.HttpContentCodec;
import io.github.mike10004.vhs.harbridge.HttpContentCodecs;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;

public class BrEncodedResponseTest {

    @Rule
    public XvfbRule xvfbRule = XvfbRule.builder().build();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private interface Client {
        String fetch(HostAndPort proxyAddress, URI url) throws Exception;
    }

    @Test
    public void seeBrEncodedResponse_chrome() throws Exception {
        BmpTests.configureJvmForChromedriver();
        seeBrEncodedResponse((proxyAddress, url) -> {
            Map<String, String> env = xvfbRule.getController().newEnvironment();
            ChromeDriverService service = new ChromeDriverService.Builder()
                    .usingAnyFreePort()
                    .withEnvironment(env)
                    .build();
            ChromeOptions options = BmpTests.createDefaultChromeOptions();
            options.addArguments("--proxy-server=" + proxyAddress);
            WebDriver driver = new ChromeDriver(service, options);
            try {
                driver.get(url.toString());
                WebElement body = driver.findElement(By.tagName("body"));
                return body.getText();
            } finally {
                driver.quit();
            }
        }, new AssertEqualsLinesWrapped());
    }

    private static class AssertEqualsLinesWrapped implements Checker {

        @Override
        public void evaluate(String expectedText, String actualText) {
            expectedText = wrap(expectedText, 80);
            actualText = wrap(actualText, 80);
            assertEquals("wrapped", expectedText, actualText);
        }
    }

    @Test
    public void seeBrEncodedResponse_jre() throws Exception {
        seeBrEncodedResponse((proxyAddress, url) -> {
            byte[] rawBytes;
            Multimap<String, String> mm = ArrayListMultimap.create();
            String contentEncoding, contentType;
            HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyAddress.getHost(), proxyAddress.getPort())));
            try {
                conn.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, "br");
                try (InputStream in = conn.getInputStream()) {
                    rawBytes = ByteStreams.toByteArray(in);
                }
                conn.getHeaderFields().forEach((name, values) -> {
                    values.forEach(value -> mm.put(name, value));
                });
                contentEncoding = conn.getHeaderField(HttpHeaders.CONTENT_ENCODING);
                contentType = conn.getHeaderField(HttpHeaders.CONTENT_TYPE);
            } finally {
                conn.disconnect();
            }
            byte[] decompressed;
            if (HttpContentCodecs.CONTENT_ENCODING_BROTLI.equalsIgnoreCase(contentEncoding)) {
                HttpContentCodec brotliCodec = HttpContentCodecs.getCodec(HttpContentCodecs.CONTENT_ENCODING_BROTLI);
                checkState(brotliCodec != null);
                decompressed = brotliCodec.decompress(rawBytes);
            } else {
                checkState(contentEncoding == null || HttpContentCodecs.CONTENT_ENCODING_IDENTITY.equalsIgnoreCase(contentEncoding));
                decompressed = Arrays.copyOf(rawBytes, rawBytes.length);
            }
            mm.entries().forEach(entry -> {
                System.out.format("%s: %s%n", entry.getKey(), entry.getValue());
            });
            Charset charset = StandardCharsets.ISO_8859_1;
            if (contentType != null) {
                charset = MediaType.parse(contentType).charset().or(StandardCharsets.ISO_8859_1);
            }
            return new String(decompressed, charset);
        }, new AssertEqualsLinesWrapped());
    }

    private interface Checker {
        void evaluate(String expectedText, String actualText);
    }

    @SuppressWarnings("SameParameterValue")
    private static String wrap(String input, int width) {
        StringBuilder b = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            b.append(input.charAt(i));
            if (i > 0 && i % width == 0) {
                b.append(System.lineSeparator());
            }
        }
        return b.toString();
    }

    private void seeBrEncodedResponse(Client client, Checker checker) throws Exception {
        File harFile = temporaryFolder.newFile();
        Resources.asByteSource(getClass().getResource("/single-entry-br-encoding.har")).copyTo(Files.asByteSink(harFile));
        Har har = new HarReader().readFromFile(harFile);
        URI url = new URI("http://www.example.com/served-with-br-encoding");
        HarResponse response = har.getLog().getEntries().get(0).getResponse();
        String harResponseContentEncoding = response.getHeaders().stream()
                .filter(header -> HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(header.getName()))
                .map(HarHeader::getValue)
                .findFirst().orElseThrow(() -> new IllegalStateException("should have content-encoding header"));
        checkState("br".equals(harResponseContentEncoding), "this test requires the entry response specify Content-Encoding: br");
        String expectedText = response.getContent().getText();

        BmpResponseListener responseListener = (requestCap, responseCap) -> {
            if (!"/favicon.ico".equals(requestCap.request.url.getPath())) {
                System.out.format("ERROR RESPONSE %s to %s%n", responseCap.response.getStatus().code(), requestCap.request);
            }
        };
        BmpResponseManufacturer responseManufacturer = BmpTests.createManufacturer(harFile, ImmutableList.of());
        BrowsermobVhsConfig vhsConfig = BrowsermobVhsConfig.builder(responseManufacturer)
                .scratchDirProvider(ScratchDirProvider.under(temporaryFolder.getRoot().toPath()))
                .responseListener(responseListener)
                .build();
        VirtualHarServer harServer = new BrowsermobVirtualHarServer(vhsConfig);
        String actualText;
        try (VirtualHarServerControl ctrl = harServer.start()) {
            HostAndPort proxyAddress = ctrl.getSocketAddress();
            actualText = client.fetch(proxyAddress, url);
        }
        System.out.println("actual:");
        System.out.println(StringUtils.abbreviateMiddle(actualText, "\n[...]\n", 256));
        checker.evaluate(expectedText, actualText);
    }
}

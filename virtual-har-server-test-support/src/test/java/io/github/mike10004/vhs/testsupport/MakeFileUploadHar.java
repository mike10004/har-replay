/*
 * Some methods in this file fall under this copyright/license:
 *
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2015 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package io.github.mike10004.vhs.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fi.iki.elonen.NanoHTTPD;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.mike10004.vhs.harbridge.FormDataPart;
import io.github.mike10004.vhs.harbridge.MultipartFormDataParser;
import io.github.mike10004.vhs.harbridge.TypedContent;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.proxy.CaptureType;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Run this program and record a HAR in Chrome to create a HAR that contains a simple file upload.
 */
public class MakeFileUploadHar {

    public static void main(String[] args) throws Exception {
        File binaryFile = File.createTempFile("image-file-for-upload", ".jpeg");
        Resources.asByteSource(MakeFileUploadHar.class.getResource("/image-for-upload.jpg")).copyTo(Files.asByteSink(binaryFile));
        main(StandardCharsets.UTF_8, binaryFile);
        binaryFile.deleteOnExit();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static net.lightbody.bmp.core.har.Har main(Charset harOutputCharset, File binaryFile) throws IOException, InterruptedException {
        File harFile = File.createTempFile("traffic-with-file-upload", ".har");
        int port = 49111;
        int proxyPort = 60999;
        String landingHtml = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<body>\n" +
                "  <form method=\"post\" action=\"/upload\" enctype=\"multipart/form-data\">\n" +
                "    <input type=\"file\" name=\"f\" required>\n" +
                "    <label>Tag <input type=\"text\" name=\"tag\" value=\"this will be url-encoded\"></label>\n" +
                "    <input type=\"submit\" value=\"Upload\">\n" +
                "  </form>\n" +
                "</body>\n" +
                "</html>";
        byte[] landingHtmlBytes = landingHtml.getBytes(StandardCharsets.UTF_8);
        Map<String, TypedContent> storage = Collections.synchronizedMap(new HashMap<>());
        NanoHTTPD nano = new NanoHTTPD(port) {
            @Override
            public Response serve(IHTTPSession session) {
                System.out.format("%s http://localhost:%s%s%n", session.getMethod(), port, session.getUri());
                URI uri = URI.create(session.getUri());
                if ("/".equals(uri.getPath())) {
                    return newFixedLengthResponse(Response.Status.OK, MediaType.HTML_UTF_8.toString(), new ByteArrayInputStream(landingHtmlBytes), landingHtmlBytes.length);
                } else if ("/upload".equals(uri.getPath())) {
                    return processUpload(session, storage);
                } else if ("/file".equals(uri.getPath())) {
                    return serveFileById(storage, session.getParameters().get("id").stream().findFirst().orElse(null));
                } else {
                    return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
                }
            }
        };
        nano.start();
        BrowserMobProxy proxy = new BrowserMobProxyServer();
        proxy.newHar();
        proxy.enableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
        proxy.start(proxyPort);
        try {
            try {
                String url = String.format("http://localhost:%s/%n", nano.getListeningPort());
                System.out.format("visiting %s%n", url);
                WebDriver driver = createWebDriver(proxyPort);
                try {
                    driver.get(url);
                    // new CountDownLatch(1).await();
                    WebElement fileInput = driver.findElement(By.cssSelector("input[type=\"file\"]"));
                    fileInput.sendKeys(binaryFile.getAbsolutePath());
                    WebElement submitButton = driver.findElement(By.cssSelector("input[type=\"submit\"]"));
                    submitButton.click();
                    new WebDriverWait(driver, 10).until(ExpectedConditions.urlMatches("^.*/file\\?id=\\S+$"));
                    System.out.format("ending on %s%n", driver.getCurrentUrl());
                } finally {
                    driver.quit();
                }
            } finally {
                nano.stop();
            }
        } finally {
            proxy.stop();
        }
        net.lightbody.bmp.core.har.Har bmpHar = proxy.getHar();
        removeConnectEntries(bmpHar.getLog().getEntries());
        String serializer = "gson";
        serialize(serializer, bmpHar, harFile, harOutputCharset);
        System.out.format("%s written (%d bytes)%n", harFile, harFile.length());
        bmpHar.getLog().getEntries().stream().map(HarEntry::getRequest).forEach(request -> {
            System.out.format("%s %s%n", request.getMethod(), request.getUrl());
        });
        return bmpHar;
    }

    private static WebDriver createWebDriver(int proxyPort) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--proxy-server=localhost:" + proxyPort);
        ChromeDriverManager.getInstance().setup();
        WebDriver driver = new ChromeDriver(options);
        return driver;
    }

    @SuppressWarnings("SameParameterValue")
    private static void serialize(String serializer, net.lightbody.bmp.core.har.Har bmpHar, File harFile, Charset harOutputCharset) throws IOException {
        bmpHar.getLog().setComment("serialized with " + serializer);
        switch (serializer){
            case "gson":
                serializeWithGson(bmpHar, harFile, harOutputCharset);
                break;
            case "jackson":
                serializeWithJackson(bmpHar, harFile, harOutputCharset);
                break;
            default:
                throw new IllegalArgumentException("serializer: " + serializer);
        }

    }

    private static void serializeWithGson(net.lightbody.bmp.core.har.Har bmpHar, File harFile, Charset HAR_OUTPUT_CHARSET) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .create();
        try (Writer out = new OutputStreamWriter(new FileOutputStream(harFile), HAR_OUTPUT_CHARSET)) {
            gson.toJson(bmpHar, out);
        }
    }

    private static void serializeWithJackson(net.lightbody.bmp.core.har.Har bmpHar, File harFile, Charset HAR_OUTPUT_CHARSET) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        try (Writer out = new OutputStreamWriter(new FileOutputStream(harFile), HAR_OUTPUT_CHARSET)) {
            objectMapper.writeValue(out, bmpHar);
        }
    }

    private static void removeConnectEntries(List<HarEntry> entries) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if ("CONNECT".equalsIgnoreCase(entries.get(i).getRequest().getMethod())) {
                indices.add(i);
            }
        }
        Collections.sort(indices);
        Collections.reverse(indices);
        for (Integer index : indices) {
            entries.remove(index.intValue());
        }
    }

    private static NanoHTTPD.Response processUpload(NanoHTTPD.IHTTPSession session, Map<String, TypedContent> storage) {
        if (NanoHTTPD.Method.POST != session.getMethod()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Not allowed: " + session.getMethod());
        }
        Long contentLength = getHeaderValueAsLong(session.getHeaders(), HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Bad Request: must contain content-length header");
        }
        byte[] bytes;
        InputStream in = session.getInputStream(); // do not close: that would close the socket, which needs to remain open to send a response
        try {
            bytes = readFully(in, contentLength);
        } catch (IOException e) {
            System.out.format("%s %s error: %s%n", session.getMethod(), session.getUri(), e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
        }
        return parseBody(getContentType(session.getHeaders()), bytes, storage);
    }

    private static byte[] readFully(InputStream stream, long unread) throws IOException {
        ByteArrayOutputStream requestDataOutput = new ByteArrayOutputStream(Math.min(Ints.saturatedCast(unread), 1024 * 1024));
        int REQUEST_BUFFER_LEN = 1024;
        byte[] buf = new byte[REQUEST_BUFFER_LEN];
        int rlen = Integer.MAX_VALUE;
        while (rlen > 0 && unread > 0) {
            rlen = stream.read(buf, 0, (int) Math.min(unread, REQUEST_BUFFER_LEN));
            unread -= rlen;
            requestDataOutput.write(buf, 0, rlen);
        }
        return requestDataOutput.toByteArray();
    }

    private static NanoHTTPD.Response redirect(String path) {
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.REDIRECT, "text/plain", "redirecting...");
        r.addHeader(HttpHeaders.LOCATION, path);
        return r;
    }

    private static NanoHTTPD.Response serveFileById(Map<String, TypedContent> storage, @Nullable String id) {
        if (id == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "'id' parameter must be specified");
        }
        TypedContent data = storage.get(id);
        if (data == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "id not found: " + id);
        }
        try {
            byte[] bytes = data.asByteSource().read();
            String contentType = data.getContentType().toString();
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, contentType, new ByteArrayInputStream(bytes), bytes.length);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
        }
    }

    private static Long getHeaderValueAsLong(Map<String, String> headers, String headerName) {
        return getHeaderValue(headers, headerName, Long::valueOf, null);
    }

    private static MediaType getContentType(Map<String, String> headers) {
        return getHeaderValue(headers, HttpHeaders.CONTENT_TYPE, MediaType::parse, MediaType.OCTET_STREAM);
    }

    private static <T> T getHeaderValue(Map<String, String> headers, String headerName, Function<? super String, T> transform, T defaultValue) {
        requireNonNull(headerName, "headerName");
        try {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (headerName.equalsIgnoreCase(header.getKey())) {
                    return transform.apply(header.getValue());
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace(System.err);
        }
        return defaultValue;
    }

    private static NanoHTTPD.Response parseBody(MediaType contentType, byte[] data, Map<String, TypedContent> storage) {
        if (contentType.is(MediaType.parse("multipart/form-data"))) {
            @Nullable String boundary = contentType.parameters().get("boundary").stream().findFirst().orElse(null);
            if (boundary == null) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Bad Request: Content type is multipart/form-data but boundary missing");
            }
            List<FormDataPart> formDataParts;
            try {
                formDataParts = new NanohttpdFormDataParser().decodeMultipartFormData(contentType, data);
            } catch (MultipartFormDataParser.BadMultipartFormDataException e) {
                e.printStackTrace(System.err);
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.lookup(MultipartFormDataParser.BadMultipartFormDataException.STATUS_CODE), "text/plain", e.getMessage());
            }
            System.out.format("%d parts parsed from request body%n", formDataParts.size());
            formDataParts.forEach(part -> {
                System.out.format("  %s%n", part);
                part.headers.forEach((name, value) -> {
                    System.out.format("    %s: %s%n", name, value);
                });
            });
            List<TypedContent> files = formDataParts.stream()
                    .filter(p -> p.contentDisposition != null && p.contentDisposition.getFilename() != null)
                    .map(p -> p.file).collect(Collectors.toList());
            if (files.isEmpty()) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Bad Request: no files");
            }
            AtomicReference<UUID> lastId = new AtomicReference<>(null);
            files.forEach(fileData -> {
                UUID id = UUID.randomUUID();
                lastId.set(id);
                storage.put(id.toString(), fileData);
            });
            return redirect("/file?id=" + lastId.get());
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Bad Request: expected content type = multipart/form-data");
        }
    }

}

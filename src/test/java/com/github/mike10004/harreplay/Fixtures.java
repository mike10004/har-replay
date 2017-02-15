/*
 * (c) 2017 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.harreplay;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Fixtures {

    private Fixtures() {}

    public static class Fixture {

        private final File harFile;
        private final String title;
        private final URI startUrl;

        private Fixture(File harFile, String title, URI startUrl) {
            this.harFile = harFile;
            this.title = title;
            this.startUrl = startUrl;
        }

        public File harFile() {
            return harFile;
        }

        public String title() {
            return title;
        }

        public URI startUrl() {
            return startUrl;
        }
    }

    private static final Fixture http = new Fixture(resourceToFile("/http.www.example.com.har"), "ABCDEFG Domain", URI.create("http://www.example.com/"));
    private static final Fixture https = new Fixture(resourceToFile("/https.www.example.com.har"), "Example Abcdef", URI.create("https://www.example.com/"));
    private static final Fixture httpsRedirect = new Fixture(resourceToFile("/https.www.example.com.redirect.har"), "Redirect Destination", URI.create("https://www.example.com/from"));

    public static Fixture http() {
        return http;
    }

    public static Fixture https() {
        return https;
    }

    public static Fixture httpsRedirect() {
        return httpsRedirect;
    }

    private static File resourceToFile(String resourcePath) {
        try {
            URL resource = ReplayManagerTester.class.getResource(resourcePath);
            if (resource == null) {
                throw new IllegalStateException("resource not found: classpath:/" + resourcePath);
            }
            File file = new File(resource.toURI());
            return file;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}

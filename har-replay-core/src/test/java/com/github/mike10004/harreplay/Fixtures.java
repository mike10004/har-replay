/*
 * (c) 2017 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.harreplay;

import io.github.bonigarcia.wdm.ChromeDriverManager;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Fixtures {

    public static final String SYSPROP_CHROMEDRIVER_VERSION = "har-replay.chromedriver.version";

    private static final String _RECOMMENDED_CHROME_DRIVER_VERSION = "2.35";

    public static String getRecommendedChromeDriverVersion() {
        return System.getProperty(SYSPROP_CHROMEDRIVER_VERSION, _RECOMMENDED_CHROME_DRIVER_VERSION);
    }

    public static class ChromeDriverSetupRule extends ExternalResource {
        @Override
        protected void before() {
            ChromeDriverManager.getInstance().version(getRecommendedChromeDriverVersion()).setup();
        }
    }

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

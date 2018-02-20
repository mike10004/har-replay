/*
 * (c) 2017 Novetta
 *
 * Created by mike
 */
package io.github.mike10004.harreplay.tests;

import com.google.common.io.Files;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

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

    public static class FixturesRule extends ExternalResource {

        private final TemporaryFolder temporaryFolder;
        private transient volatile Fixtures fixtures;

        public FixturesRule() {
            this(new TemporaryFolder());
        }

        public FixturesRule(TemporaryFolder temporaryFolder) {
            this.temporaryFolder = temporaryFolder;
        }

        @Override
        protected void before() throws IOException {
            temporaryFolder.create();
            fixtures = Fixtures.inDirectory(temporaryFolder.getRoot().toPath());
        }

        @Override
        protected void after() {
            temporaryFolder.delete();
        }

        public Fixtures getFixtures() {
            return fixtures;
        }
    }

    public static Fixtures inDirectory(Path scratchDir) throws IOException {
        return new Fixtures(scratchDir);
    }

    public static FixturesRule asRule() {
        return new FixturesRule();
    }

    public static FixturesRule asRule(TemporaryFolder temporaryFolder) {
        return new FixturesRule(temporaryFolder);
    }

    private Fixtures(Path scratchDir) throws IOException {
        http = new Fixture(copyResourceToFile("/http.www.example.com.har", scratchDir), "ABCDEFG Domain", URI.create("http://www.example.com/"));
        https = new Fixture(copyResourceToFile("/https.www.example.com.har", scratchDir), "Example Abcdef", URI.create("https://www.example.com/"));
        httpsRedirect = new Fixture(copyResourceToFile("/https.www.example.com.redirect.har", scratchDir), "Redirect Destination", URI.create("https://www.example.com/from"));
    }

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

    private final Fixture http;
    private final Fixture https;
    private final Fixture httpsRedirect;

    public Fixture http() {
        return http;
    }

    public Fixture https() {
        return https;
    }

    public Fixture httpsRedirect() {
        return httpsRedirect;
    }

    private File copyResourceToFile(String resourcePath, Path scratchDir) throws IOException {
        URL resource = Fixtures.class.getResource(resourcePath);
        if (resource == null) {
            throw new FileNotFoundException("resource not found: classpath:/" + resourcePath);
        }
        File file = File.createTempFile("har-replay-fixture", ".tmp", scratchDir.toFile());
        com.google.common.io.Resources.asByteSource(resource).copyTo(Files.asByteSink(file));
        return file;
    }

}

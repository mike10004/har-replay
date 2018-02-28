/*
 * (c) 2017 Novetta
 *
 * Created by mike
 */
package io.github.mike10004.harreplay.tests;

import com.google.common.base.Suppliers;
import com.google.common.io.Files;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Supplier;

public class Fixtures {

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

    public static Fixtures inDirectory(Path scratchDir) {
        return new Fixtures(scratchDir);
    }

    public static FixturesRule asRule() {
        return new FixturesRule();
    }

    @SuppressWarnings("unused")
    public static FixturesRule asRule(TemporaryFolder temporaryFolder) {
        return new FixturesRule(temporaryFolder);
    }

    private Fixtures(Path scratchDir) {
        http = Suppliers.memoize(() -> new Fixture("example-http", copyResourceToFile("/http.www.example.com.har", scratchDir), "ABCDEFG Domain", URI.create("http://www.example.com/")));
        https = Suppliers.memoize(() -> new Fixture("example-https", copyResourceToFile("/https.www.example.com.har", scratchDir), "Example Abcdef", URI.create("https://www.example.com/")));
        httpsRedirect = Suppliers.memoize(() -> new Fixture("example-redirect", copyResourceToFile("/https.www.example.com.redirect.har", scratchDir), "Redirect Destination", URI.create("https://www.example.com/from")));
    }

    public static class Fixture {

        private final String name;
        private final File harFile;
        private final String title;
        private final URI startUrl;

        private Fixture(String name, File harFile, String title, URI startUrl) {
            this.name = name;
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

        public String toString() {
            return String.format("Fixture{name=%s, startUrl=%s}", name, startUrl);
        }
    }

    private final Supplier<Fixture> http;
    private final Supplier<Fixture> https;
    private final Supplier<Fixture> httpsRedirect;

    public Fixture http() {
        return http.get();
    }

    public Fixture https() {
        return https.get();
    }

    public Fixture httpsRedirect() {
        return httpsRedirect.get();
    }

    private static File copyResourceToFile(String resourcePath, Path scratchDir) {
        try {
            URL resource = Fixtures.class.getResource(resourcePath);
            if (resource == null) {
                throw new FileNotFoundException("resource not found: classpath:/" + resourcePath);
            }
            File file = File.createTempFile("har-replay-fixture", ".tmp", scratchDir.toFile());
            com.google.common.io.Resources.asByteSource(resource).copyTo(Files.asByteSink(file));
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

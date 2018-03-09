package io.github.mike10004.harreplay.tests;

import com.google.common.io.Files;
import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.harreplay.ReplayServerConfig.Mapping;
import io.github.mike10004.harreplay.ReplayServerConfig.Replacement;
import io.github.mike10004.harreplay.tests.Fixtures.Fixture;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public abstract class ReplayManagerTestBase extends ReplayManagerTestFoundation {

    @Rule
    public final Timeout timeout = new Timeout(15, TimeUnit.SECONDS);

    @Test
    public void http() throws Exception {
        System.out.println("\n\nhttp\n");
        File harFile = fixturesRule.getFixtures().http().harFile();
        fetchAndCompareToHar(harFile, fixturesRule.getFixtures().http().startUrl());
    }

    @Test
    public void https() throws Exception {
        System.out.println("\n\nhttps\n");
        Fixture fixture = fixturesRule.getFixtures().https();
        File harFile = fixture.harFile();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .build();
        URI uri = fixture.startUrl();
        ApacheRecordingClient client = newApacheClient(uri, true);
        fetchAndExamine(harFile, uri, client, config, matcher("title absent", describing(content -> content.contains(fixture.title()), "text must contain title " + fixture.title())));
    }

    @Test
    public void literalMappingToCustomFile() throws Exception {
        File customContentFile = temporaryFolder.newFile();
        String customContent = "my custom string";
        Files.asCharSink(customContentFile, UTF_8).write(customContent);
        Fixture fixture = fixturesRule.getFixtures().http();
        URI uri = fixture.startUrl();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .map(Mapping.literalToFile(uri.toString(), customContentFile))
                .build();
        File harFile = fixture.harFile();
        ApacheRecordingClient client = newApacheClient(uri, false);
        fetchAndExamine(harFile, uri, client, config, matcher("custom content expected", describing(customContent::equals, StringUtils.abbreviate(customContent, 64))));
    }

    @Test
    public void literalReplacement() throws Exception {
        String replacementText = "I Eat Your Brain";
        Fixture fixture = fixturesRule.getFixtures().https();
        File harFile = fixture.harFile();
        URI uri = fixture.startUrl();
        ReplayServerConfig config = ReplayServerConfig.builder()
                .replace(Replacement.literal(fixture.title(), replacementText))
                .build();
        ApacheRecordingClient client = newApacheClient(uri, true);
        fetchAndExamine(harFile, uri, client, config, matcher("replacement text absent", describing(responseContent -> responseContent.contains(replacementText), replacementText)));
    }

    @Test
    public void http_unmatchedReturns404() throws Exception {
        System.out.println("\n\nhttp_unmatchedReturns404\n");
        Path tempDir = temporaryFolder.getRoot().toPath();
        File harFile = fixturesRule.getFixtures().http().harFile();
        ApacheRecordingClient client = newApacheClient(URI.create("http://www.google.com/"), false);
        int port = ReplayManagerTester.findReservedPort(getReservedPortSystemPropertyName());
        ResponseSummary response = createTester(tempDir, harFile, ReplayServerConfig.empty())
                .exercise(client, port)
                .values().iterator().next();
        System.out.format("response: %s%n", response.statusLine);
        System.out.format("response text:%n%s%n", response.entity);
        assertEquals("status", HttpStatus.SC_NOT_FOUND, response.statusLine.getStatusCode());
    }

    @Test
    public void https_transformLocationResponseHeader() throws Exception {
        System.out.println("\n\nhttps_transformLocationResponseHeader\n");
        Fixture fixture = fixturesRule.getFixtures().httpsRedirect();
        File harFile = fixture.harFile();
        System.out.format("using fixture %s with har %s%n", fixture, harFile);
        ReplayServerConfig config = ReplayServerConfig.builder()
                .transformResponse(createLocationHttpsToHttpTransform())
                .build();
        URI uri = fixture.startUrl();
        ApacheRecordingClient client = newApacheClient(uri, true);
        fetchAndExamine(harFile, uri, client, config, matcher("expect fixture title to be present", describing(content -> content.contains(fixture.title()), "expected title " + fixture.title())));
    }

}
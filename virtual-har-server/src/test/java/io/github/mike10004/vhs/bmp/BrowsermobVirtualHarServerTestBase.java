package io.github.mike10004.vhs.bmp;

import com.browserup.harreader.HarReaderException;
import com.browserup.harreader.model.HarEntry;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.ReplaySessionState;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BrowsermobVirtualHarServerTestBase extends VirtualHarServerTestBase {

    protected static final String KEY_KEYSTORE_DATA = "keystoreData";

    private static final String CUSTOM_HEADER_NAME = "X-Virtual-Har-Server-Unit-Test";

    protected List<String> requests;
    protected List<String> customValues;

    @Before
    public void setUp() throws IOException {
        customValues = Collections.synchronizedList(new ArrayList<>());
        requests = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    protected final VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TestContext context) throws IOException {
        BrowsermobVhsConfig config = createServerConfig(port, harFile, entryMatcherFactory, context);
        return new BrowsermobVirtualHarServer(config);
    }

    protected final BrowsermobVhsConfig createServerConfig(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TestContext context) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new com.browserup.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        EntryParser<HarEntry> parser = HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        HarReplayManufacturer responseManufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList()) {
            @Override
            public ResponseCapture manufacture(ReplaySessionState state, RequestCapture capture) {
                requests.add(String.format("%s %s", capture.request.method, capture.request.url));
                return super.manufacture(state, capture);
            }
        };
        Path scratchParent = temporaryFolder.getRoot().toPath();
        BmpResponseListener responseFilter = new HeaderAddingFilter(CUSTOM_HEADER_NAME, () -> {
            String value = UUID.randomUUID().toString();
            customValues.add(value);
            return value;
        });
        BrowsermobVhsConfig.Builder configBuilder = BrowsermobVhsConfig.builder(responseManufacturer)
                .port(port)
                .responseListener(responseFilter)
                .scratchDirProvider(ScratchDirProvider.under(scratchParent));
        TlsMode tlsMode = context.get(KEY_TLS_MODE);
        if (tlsMode == TlsMode.SUPPORT_REQUIRED || tlsMode == TlsMode.PREDEFINED_CERT_SUPPORT) {
            try {
                String commonName = "localhost";
                KeystoreData keystoreData = BmpTests.generateKeystoreForUnitTest(commonName);
                NanohttpdTlsEndpointFactory tlsEndpointFactory = NanohttpdTlsEndpointFactory.create(keystoreData, null);
                configBuilder.tlsEndpointFactory(tlsEndpointFactory);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        if (tlsMode == TlsMode.PREDEFINED_CERT_SUPPORT) {
            KeystoreData keystoreData = context.get(KEY_KEYSTORE_DATA);
            configBuilder.certificateAndKeySource(keystoreData.asCertificateAndKeySource());
        }
        return configBuilder.build();
    }

}

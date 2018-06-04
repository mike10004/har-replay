package io.github.mike10004.vhs.bmp;

import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;

public class SstoehrEntryParserTest extends EntryParserTestBase<HarEntry> {

    @Override
    protected EntryParser<HarEntry> createParser() {
        return HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge());
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected HarEntry createEntryWithRequest(String method, String urlStr, String... headers) {
        HarRequest request = new HarRequest();
        request.setMethod(HttpMethod.GET);
        request.setUrl(urlStr);
        for (int i = 0; i < headers.length; i += 2) {
            HarHeader header = new HarHeader();
            header.setName((headers[i]));
            header.setValue(headers[i+1]);
            request.getHeaders().add(header);
        }
        HarEntry entry = new HarEntry();
        entry.setRequest(request);
        return entry;
    }

}
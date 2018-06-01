package io.github.mike10004.vhs.bmp;

import com.google.gson.GsonBuilder;
import io.github.mike10004.vhs.bmp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.bmp.repackaged.fi.iki.elonen.NanoHTTPD.Response.Status;
import io.github.mike10004.vhs.bmp.HarMaker.EntrySpec;
import io.github.mike10004.vhs.bmp.HarMaker.HttpCallback;
import net.lightbody.bmp.core.har.Har;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HarMakerTest {

    @org.junit.Ignore // not a test of a production class
    @Test
    public void makeHar() throws Exception {
        List<EntrySpec> specs = Arrays.asList(
                new EntrySpec(RequestSpec.get(URI.create("http://example.com/one")), NanoHTTPD.newFixedLengthResponse(Status.OK, "text/plain", "one")),
                new EntrySpec(RequestSpec.get(URI.create("http://example.com/two")), NanoHTTPD.newFixedLengthResponse(Status.OK, "text/plain", "two"))
        );
        HarMaker maker = new HarMaker();
        Har har = maker.produceHar(specs, new HttpCallback() {
            @Override
            public void requestSent(RequestSpec requestSpec) {
                System.out.format("request sent: %s %s%n", requestSpec.method, requestSpec.url);
            }

            @Override
            public void responseConsumed() {
                System.out.format("response consumed");
            }
        });
        assertEquals("num entries", specs.size(), har.getLog().getEntries().size());
        new GsonBuilder().setPrettyPrinting().create().toJson(har, System.out);
        System.out.println();
    }
}

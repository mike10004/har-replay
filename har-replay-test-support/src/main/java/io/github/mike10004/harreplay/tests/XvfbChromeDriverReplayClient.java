package io.github.mike10004.harreplay.tests;

import com.github.mike10004.xvfbtesting.XvfbRule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import io.github.mike10004.harreplay.ReplaySessionControl;
import io.github.mike10004.harreplay.tests.ReplayManagerTester.ReplayClient;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class XvfbChromeDriverReplayClient implements ReplayClient<Multimap<URI, String>> {

    private final XvfbRule xvfb;
    private final ChromeOptionsProducer optionsProducer;
    private final ImmutableList<URI> urisToGet;

    public XvfbChromeDriverReplayClient(XvfbRule xvfbRule, ChromeOptionsProducer optionsProducer, URI...urisToGet) {
        super();
        this.xvfb = requireNonNull(xvfbRule);
        this.optionsProducer = requireNonNull(optionsProducer);
        this.urisToGet = ImmutableList.copyOf(urisToGet);
    }

    @Override
    public Multimap<URI, String> useReplayServer(Path tempDir, ReplaySessionControl sessionControl) throws Exception {
        return useReplayServer(sessionControl, urisToGet);
    }

    public Multimap<URI, String> useReplayServer(ReplaySessionControl sessionControl, URI firstUri, URI...otherUris) throws IOException {
        return useReplayServer(sessionControl, Lists.asList(firstUri, otherUris));
    }

    public Multimap<URI, String> useReplayServer(ReplaySessionControl sessionControl, Iterable<URI> urisToGet) throws IOException {
        HostAndPort proxy = sessionControl.getSocketAddress();
        Consumer<? super ChromeOptions> optionsMods = optionsProducer.produceOptions(proxy);
        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingAnyFreePort()
                .withEnvironment(xvfb.getController().newEnvironment())
                .build();
        ChromeOptions options = new ChromeOptions();
        optionsMods.accept(options);
        ChromeDriver driver = new ChromeDriver(service, options);
        ChromeDriverKillHook.getInstance().add(driver);
        try {
            Multimap<URI, String> pageSources = ArrayListMultimap.create();
            for (URI uri : urisToGet) {
                driver.get(uri.toString());
                String pageSource = driver.getPageSource();
                pageSources.put(uri, pageSource);
            }
            return pageSources;
        } finally {
            driver.quit();
            ChromeDriverKillHook.getInstance().remove(driver);
        }
    }
}

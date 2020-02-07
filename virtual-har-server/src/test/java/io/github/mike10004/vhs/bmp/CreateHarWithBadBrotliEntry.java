package io.github.mike10004.vhs.bmp;

import io.github.mike10004.harreplay.tests.ChromeDriverSetupRule;
import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.harreader.model.Har;
import com.browserup.bup.proxy.CaptureType;
import io.github.mike10004.seleniumcapture.BrowserUpHars;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

public class CreateHarWithBadBrotliEntry {

    public static void main(String[] args) throws Exception {
        ChromeDriverSetupRule.doSetup();
        BrowserUpProxy proxy = new BrowserUpProxyServer();
        int port = 4411;
        proxy.enableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
        proxy.newHar();
        proxy.start(port);
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--proxy-server=localhost:" + port);
            ChromeDriver driver = new ChromeDriver(options);
            try {
                driver.get("http://httpbin.org/brotli");
            } finally {
                driver.quit();
            }
        } finally {
            proxy.stop();
        }

        Har har = proxy.endHar();
        File harFile = File.createTempFile("visit-brotli-page-through-bmp", ".har");
        BrowserUpHars.writeHar(har, harFile, StandardCharsets.UTF_8);
        System.out.format("%s written%n", harFile);
    }
}

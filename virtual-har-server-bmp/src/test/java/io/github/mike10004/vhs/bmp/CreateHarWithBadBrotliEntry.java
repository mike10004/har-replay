package io.github.mike10004.vhs.bmp;

import io.github.bonigarcia.wdm.ChromeDriverManager;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.CaptureType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.util.EnumSet;

public class CreateHarWithBadBrotliEntry {

    public static void main(String[] args) throws Exception {
        ChromeDriverManager.getInstance().setup();
        BrowserMobProxy proxy = new BrowserMobProxyServer();
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
        har.writeTo(harFile);
        System.out.format("%s written%n", harFile);
    }
}

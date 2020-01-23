package io.github.mike10004.harreplay.tests;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.rules.ExternalResource;

public class FirefoxDriverSetupRule extends ExternalResource {

    private static volatile boolean performed = false;

    @Override
    protected void before() {
        if (!performed) {
            doSetup();
        }
    }

    public static void doSetup() {
        WebDriverManager.firefoxdriver().setup();
        performed = true;
    }
}

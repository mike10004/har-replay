package io.github.mike10004.harreplay.tests;

import io.github.bonigarcia.wdm.ChromeDriverManager;
import org.junit.rules.ExternalResource;

public class ChromeDriverSetupRule extends ExternalResource {
    @Override
    protected void before() {
        ChromeDriverManager.getInstance().version(Tests.getRecommendedChromeDriverVersion()).setup();
    }
}

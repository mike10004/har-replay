package io.github.mike10004.harreplay.tests;

import com.google.common.base.Suppliers;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class ChromeDriverKillHook {

    private final Set<WebDriver> chromeDriverInstances;

    private ChromeDriverKillHook() {
        chromeDriverInstances = Collections.synchronizedSet(new HashSet<>());
    }

    private static Supplier<ChromeDriverKillHook> instanceSupplier = Suppliers.memoize(() -> {
        ChromeDriverKillHook hook = new ChromeDriverKillHook();
        Runtime.getRuntime().addShutdownHook(new Thread(hook::execute));
        return hook;
    });

    public static ChromeDriverKillHook getInstance() {
        return instanceSupplier.get();
    }

    public void add(ChromeDriver instance) {
        chromeDriverInstances.add(instance);
    }

    public void remove(ChromeDriver instance) {
        chromeDriverInstances.remove(instance);
    }

    private void execute() {
        chromeDriverInstances.forEach(driver -> {
            System.out.println("@AfterClass quitting " + driver);
            driver.quit();
            System.out.println("@AfterClass did quit " + driver);
        });

    }
}

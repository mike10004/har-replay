/*
 *
 */
package io.github.mike10004.harreplay.tests;

import com.google.common.net.HostAndPort;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("Duplicates")
public abstract class BrowseHarWithChromeExample extends ReadmeExample {

    private static final int CLOSED_POLL_INITIAL_DELAY_MS = 1000;
    private static final int CLOSED_POLL_INTERVAL_MS = 100;
    private final Object windowClosedSignal = new Object();

    protected ChromeOptions createChromeOptions(Path tempDir, HostAndPort proxy) throws IOException {
        return ChromeOptionsProducer.standard().produceOptions(proxy);
    }

    @Override
    protected void doSomethingWithProxy(String host, int port) throws IOException {
        Path tempDir = FileUtils.getTempDirectory().toPath();
        HostAndPort proxy = HostAndPort.fromParts(host, port);
        System.out.format("har replay proxy listening at %s%n", proxy);
        ChromeDriverSetupRule.doSetup();
        ChromeOptions options = createChromeOptions(tempDir, proxy);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        ChromeDriver driver = new ChromeDriver(options);
        try {
            /*
             * Ideally we'd listen for an event indicating that the browser had been closed,
             * but no such event exists. So instead we poll for the window state.
             */

            WindowClosedCheck checker = new WindowClosedCheck(driver);
            ScheduledFuture<?> future = executor.scheduleWithFixedDelay(checker, CLOSED_POLL_INITIAL_DELAY_MS, CLOSED_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            checker.setScheduledFuture(future);
            synchronized (windowClosedSignal) {
                windowClosedSignal.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace(System.err);
        } finally {
            driver.quit();
            executor.shutdownNow();
        }
    }

    private class WindowClosedCheck implements Runnable {

        private final AtomicLong counter = new AtomicLong();
        private final ChromeDriver driver;
        private ScheduledFuture<?> scheduledFuture;
        private boolean aborted;

        private WindowClosedCheck(ChromeDriver driver) {
            this.driver = driver;
        }

        @Override
        public void run() {
            counter.incrementAndGet();
            if (!aborted) {
                WebDriver.Options options = driver.manage();
                WebDriver.Window window = options.window();
                /*
                 *  The API does not provide a method to check whether the window is closed,
                 *  but this method will throw an NPE if the window is closed.
                 */
                try {
                    window.getPosition();
                } catch (NullPointerException e) {
                    System.out.format("[%d] could not obtain window position due to %s%n", counter.get(), e.toString());
                    abort();
                }
            }
        }

        private void abort() {
            if (!aborted) {
                aborted = true;
                ScheduledFuture<?> scheduledFuture = this.scheduledFuture;
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(false);
                }
                synchronized (windowClosedSignal) {
                    windowClosedSignal.notifyAll();
                }
            }
        }

        public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
            this.scheduledFuture = scheduledFuture;
        }
    }

}

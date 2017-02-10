package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ReplayManagerTester.ReplayClient;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.function.Predicate;

public class ReplayManagerPractice {

    public static void main(String[] args) throws Exception {
        File buildDir = new File(System.getProperty("user.dir"), "target");
        Path tempDir = java.nio.file.Files.createTempDirectory(buildDir.toPath(), "NodeServerReplayTest");
        File harFile;
        if (args.length > 0) {
            harFile = new File(args[0]);
        } else {
            harFile = new File(ReplayManagerPractice.class.getResource("/http.www.example.com.har").toURI());
        }
        ReplayManagerTester tester = new ReplayManagerTester(tempDir, harFile);
        tester.exercise(new InteractiveChromeDriverClient(), null);
    }

    private static class InteractiveChromeDriverClient implements ReplayClient<Void> {

        @Override
        public Void useReplayServer(Path tempDir, HostAndPort proxy) throws Exception {
            File profileDir = tempDir.resolve("chrome-profile").toFile();
            profileDir.mkdirs();
            if (!profileDir.isDirectory()) {
                throw new IOException("could not create directory for Chrome profile");
            }
            ChromeDriverManager.getInstance().setup("2.27");
            ChromeOptions chromeOptions = new ChromeOptions();
            File switcherooCrxFile = File.createTempFile("modified-switcheroo", ".crx", tempDir.toFile());
            ModifiedSwitcheroo.getExtensionCrxByteSource().copyTo(Files.asByteSink(switcherooCrxFile));
            chromeOptions.addExtensions(switcherooCrxFile);
            chromeOptions.addArguments("--user-data-dir=" + profileDir.getAbsolutePath(),
                    "--proxy-server=" + proxy.toString());
            ChromeDriver driver = new ChromeDriver(chromeOptions);
            try {
                blockUntilInputOrEof("exit"::equalsIgnoreCase);
            } finally {
                driver.quit();
            }
            return (Void) null;
        }

        private static String blockUntilInputOrEof(Predicate<? super String> linePredicate) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()))) {
                String line;
                System.out.print("enter 'exit' to quit: ");
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if (linePredicate.test(line)) {
                        return line;
                    }
                }
            }
            return null;
        }
    }

}

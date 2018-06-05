package io.github.mike10004.harreplay.exec;

import com.google.common.base.Suppliers;
import org.junit.Assume;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;

public class ExecTests {

    private ExecTests() {}

    private static final Supplier<Properties> testPropertiesSupplier = Suppliers.memoize(() -> {
        Properties testProperties = new Properties();
        try (InputStream in = ExecTests.class.getResourceAsStream("/tests.properties")) {
            if (in == null) {
                throw new FileNotFoundException("/tests.properties must be on test classpath");
            }
            testProperties.load(in);
            return testProperties;
        } catch (IOException e) {
            throw new IllegalStateException("failed to load test properties", e);
        }
    });

    public static Properties getTestProperties() {
        return testPropertiesSupplier.get();
    }

    public static String getTestProperty(String propertyName) {
        return getTestProperties().getProperty(propertyName);
    }

    public static String getTestProperty(String propertyName, String defaultValue) {
        return getTestProperties().getProperty(propertyName, defaultValue);
    }

    public static boolean isExecAssemblySkipped() {
        return Boolean.parseBoolean(System.getProperty(SYSPROP_SKIP_EXEC_ASSEMBLY, "false"));
    }

    private static final String SYSPROP_SKIP_EXEC_ASSEMBLY = "exec.assembly.skip";

    public static void assumeExecAssemblyNotSkipped() {
        Assume.assumeFalse("system property " + SYSPROP_SKIP_EXEC_ASSEMBLY + " is true, so this will be skipped", isExecAssemblySkipped());
    }
}
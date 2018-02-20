package io.github.mike10004.harreplay.exec;

import com.google.common.base.Suppliers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;

public class Tests {

    private Tests() {}

    private static final Supplier<Properties> testPropertiesSupplier = Suppliers.memoize(() -> {
        Properties testProperties = new Properties();
        try (InputStream in = Tests.class.getResourceAsStream("/tests.properties")) {
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
}
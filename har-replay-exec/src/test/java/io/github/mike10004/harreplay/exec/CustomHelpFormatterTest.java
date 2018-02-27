package io.github.mike10004.harreplay.exec;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionDescriptor;
import joptsimple.OptionParser;
import joptsimple.ValueConverter;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static org.junit.Assert.assertEquals;

public class CustomHelpFormatterTest {

    @Test
    public void extractTypeIndicator() {
        testExtraction(spec -> spec.ofType(SomeEnum.class), "string", "enum type option");
        testExtraction(spec -> spec.ofType(String.class), "string", "string type option");
        testExtraction(spec -> spec.ofType(Integer.class), "int", "Integer type option");
        testExtraction(spec -> spec.ofType(Long.class), "long", "Long type option");
        testExtraction(spec -> spec.ofType(Byte.class), "byte", "Byte type option");
        testExtraction(spec -> spec.withValuesConvertedBy(new CharacterValueConverter()), "char", "Character type option");
        testExtraction(spec -> spec.ofType(File.class), "pathname", "File type option");
        testExtraction(spec -> spec.withValuesConvertedBy(new PathValueConverter()), "pathname", "Path type option");
        testExtraction(spec -> spec.ofType(Constructible.class), "string", "custom class with string constructor option");
    }

    private void testExtraction(Function<ArgumentAcceptingOptionSpec<?>, OptionDescriptor> descriptorCreator, String expected, String message) {
        ArgumentAcceptingOptionSpec<?> spec = new OptionParser().accepts("o").withRequiredArg();
        OptionDescriptor descriptor = descriptorCreator.apply(spec);
        String actual = new CustomHelpFormatter().extractTypeIndicator(descriptor);
        System.out.format("%s -> %s%n", descriptor.argumentTypeIndicator(), actual);
        assertEquals(message, expected, actual);
    }

    @SuppressWarnings("unused")
    private static class Constructible {
        public Constructible(String ignore) {}
    }

    private enum SomeEnum {

    }

    private static class CharacterValueConverter implements ValueConverter<Character> {

        @Override
        public Character convert(String value) {
            checkArgument(value != null && value.length() == 1, "value must be single character");
            return value.charAt(0);
        }

        @Override
        public Class<? extends Character> valueType() {
            return Character.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }

    private static class PathValueConverter implements ValueConverter<Path> {

        @Override
        public Path convert(String value) {
            return new File(value).toPath();
        }

        @Override
        public Class<? extends Path> valueType() {
            return Path.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }
}
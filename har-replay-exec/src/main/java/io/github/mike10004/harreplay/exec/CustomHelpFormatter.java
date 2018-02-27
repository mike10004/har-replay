package io.github.mike10004.harreplay.exec;

import com.google.common.primitives.Primitives;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionDescriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class CustomHelpFormatter extends BuiltinHelpFormatter {

    private static final Set<Class<?>> PATHNAME_CLASSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            java.io.File.class,
            java.nio.file.Path.class)));

    public CustomHelpFormatter() {
        super(80, 2);
    }

    @Override
    protected String extractTypeIndicator(OptionDescriptor descriptor) {
        String indicator = descriptor.argumentTypeIndicator();
        if (indicator != null) {
            Class<?> indicatorAsClass = null;
            try {
                indicatorAsClass = Class.forName(indicator);
            } catch (ClassNotFoundException ignore) {
            }
            if (indicatorAsClass != null) {
                indicatorAsClass = Primitives.unwrap(indicatorAsClass);
                if (Primitives.allPrimitiveTypes().contains(indicatorAsClass)) {
                    return indicatorAsClass.getName();
                }
                if (PATHNAME_CLASSES.contains(indicatorAsClass)) {
                    return "pathname";
                }
            }
        }
        return "string";
    }

}

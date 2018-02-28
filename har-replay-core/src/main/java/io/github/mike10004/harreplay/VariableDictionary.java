package io.github.mike10004.harreplay;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Interface for a mapping of variable names to values. The variable names
 * that are available are implementation-independent. The VHS implementation
 * makes some request data available; see {@code ReplacingInterceptor} in that module.
 */
public interface VariableDictionary {

    /**
     * Returns the appropriate substitution for a given variable name. If no
     * substitution is found, null is returned. If the substition result is undefined,
     * then an empty optional is returned.
     * if the actual substitution yields null
     * @param variableName the variable name; must not be null
     * @return an optional describing the substitution, or null if no substitution found
     */
    @Nullable
    Optional<String> substitute(String variableName);

}

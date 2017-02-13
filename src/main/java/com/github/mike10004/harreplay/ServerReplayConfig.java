package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ServerReplayConfig.StringLiteral.StringLiteralTypeAdapter;
import com.google.common.collect.ImmutableList;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class that represents the configuration that a server replay process uses. This object's structure
 * matches the structure of the object that the Node server-replay module uses. See that project's
 * documentation for information on the various parameters.
 *
 * <p>This class is not currently deserializable, but it serializes to the JSON format required
 * by the server-replay Node module just fine.
 * </p>
 */
public class ServerReplayConfig {

    /**
     * Version. Use <code>1</code> for now.
     */
    public final int version;

    /**
     * Maps from URLs to file system paths. Use these to serve matching URLs from the filesystem instead of the HAR.
     */
    public final ImmutableList<Mapping> mappings;

    /**
     * Definitions of replacements to execute for in the body of textual response content.
     */
    public final ImmutableList<Replacement> replacements;

    private ServerReplayConfig() {
        this(1, ImmutableList.of(), ImmutableList.of());
    }

    /**
     * Constructs a new empty configuration object.
     * @return the configuration object
     */
    @SuppressWarnings("unused")
    public static ServerReplayConfig empty() {
        return new ServerReplayConfig();
    }

    /**
     * Constructs an instance of the class.
     * @param version       the version
     * @param mappings the mappings
     * @param replacements the replacements
     */
    public ServerReplayConfig(int version, Iterable<Mapping> mappings, Iterable<Replacement> replacements) {
        this.version = version;
        this.mappings = ImmutableList.copyOf(mappings);
        this.replacements = ImmutableList.copyOf(replacements);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Interface for classes that represent the {@code match} field of a {@link Mapping}.
     */
    public interface MappingMatch {}

    /**
     * Interface for classes that represent the {@code path} field of a {@link Mapping}.
     */
    public interface MappingPath {}

    /**
     * Interface for classes that represent the {@code match} field of a {@link Replacement}.
     */
    public interface ReplacementMatch {}

    /**
     * Interface for classes that represent the {@code replace} field of a {@link Replacement}.
     */
    public interface ReplacementReplace {}

    /**
     * Mapping from URL to filesystem pathname. Match field is a string or Javascript regex, and path is a string.
     * Path can contain $n references to substitute capture groups from match.
     */
    @SuppressWarnings("unused")
    public final static class Mapping {

        public final MappingMatch match;
        public final MappingPath path;

        public Mapping(MappingMatch match, MappingPath path) {
            this.match = checkNotNull(match);
            this.path = checkNotNull(path);
        }

        /**
         * Constructs a new mapping given a matcher and a destination file.
         * @param match the matcher
         * @param file the file to serve
         * @return a mapping instance
         */
        public static Mapping toFile(MappingMatch match, File file) {
            return new Mapping(match, new StringLiteral(file.getAbsolutePath()));
        }

        public static Mapping literalToFile(String literalMatch, File file) {
            return Mapping.toFile(new StringLiteral(literalMatch), file);
        }

        public static Mapping regexToFile(String regex, File file) {
            return Mapping.toFile(new RegexHolder(regex), file);
        }

        /**
         * Constructs a new mapping given a matcher and a destination file.
         * @param match the matcher
         * @param path the file to serve; can contain $n groups from a match regex
         * @return a mapping instance
         */
        public static Mapping toPath(MappingMatch match, String path) {
            return new Mapping(match, new StringLiteral(path));
        }

        public static Mapping literalToPath(String literalMatch, String path) {
            return Mapping.toPath(new StringLiteral(literalMatch), path);
        }

        public static Mapping regexToPath(String regex, String path) {
            return Mapping.toPath(new RegexHolder(regex), path);
        }

    }

    /**
     * Class that represents a string value for a field of a {@link Mapping} or {@link Replacement} instance.
     */
    @com.google.gson.annotations.JsonAdapter(StringLiteralTypeAdapter.class)
    public static class StringLiteral implements MappingMatch, MappingPath, ReplacementMatch, ReplacementReplace {

        public final String value;

        public StringLiteral(String value) {
            this.value = checkNotNull(value);
        }

        public static class StringLiteralTypeAdapter extends TypeAdapter<StringLiteral> {

            @Override
            public void write(JsonWriter out, StringLiteral value) throws IOException {
                out.value(value.value);
            }

            @Override
            public StringLiteral read(JsonReader in) throws IOException {
                String value = in.nextString();
                return new StringLiteral(value);
            }
        }
    }

    /**
     * Class that represents a variable object. These are used in {@link Replacement}s.
     * See https://github.com/Stuk/server-replay.
     */
    public static class VariableHolder implements ReplacementMatch, ReplacementReplace {

        public final String var;

        @SuppressWarnings("unused") // for deserialization
        private VariableHolder() {
            var = null;
        }

        public VariableHolder(String var) {
            this.var = checkNotNull(var);
        }
    }

    /**
     * Class that represents a regex object. Instances of this class are used to define {@link Mapping}
     * or {@link Replacement} matchers.
     */
    public static class RegexHolder implements MappingMatch, ReplacementMatch {

        /**
         * Regex in Javascript syntax.
         */
        public final String regex;

        @SuppressWarnings("unused") // for deserialization
        private RegexHolder() {
            regex = null;
        }

        public RegexHolder(String regex) {
            this.regex = checkNotNull(regex);
        }
    }

    /**
     * Replacement strategy for textual response content. {@link #match Match} field is a string, Javascript regex or variable,
     * and {@link #replace} field is a string or variable. The {@code replace} field can contain $n references to
     * substitute capture groups from match.
     */
    @SuppressWarnings("unused")
    public static final class Replacement {

        public final ReplacementMatch match;
        public final ReplacementReplace replace;

        @SuppressWarnings("unused") // for deserialization
        private Replacement() {
            match = null;
            replace = null;
        }

        public Replacement(ReplacementMatch match, ReplacementReplace replace) {
            this.match = match;
            this.replace = replace;
        }

        /**
         * Constructs a replacement that swaps exact instances of a string literal with another string.
         * @param match the string to replace
         * @param replace the value to put in the replaced string's place
         * @return a new replacement
         */
        @SuppressWarnings("SameParameterValue")
        public static Replacement literal(String match, String replace) {
            return new Replacement(new StringLiteral(match), new StringLiteral(replace));
        }

        /**
         * Constructs a replacement that swaps substrings that match a regex with a replacement string.
         * @param matchRegex Javascript regex to match
         * @param replace replacement value; can use $n groups
         * @return a new  replacement instance
         */
        public static Replacement regexToString(String matchRegex, String replace) {
            return new Replacement(new RegexHolder(matchRegex), new StringLiteral(replace));
        }

        /**
         * Constructs a replacement that swaps substrings that match the value of a variable
         * with the value of another variable.
         * @param matchVariable variable to match, e.g. {@code entry.request.parsedUrl.query.callback}
         * @param replaceVariable variable representing the replacement value, e.g. {@code request.parsedUrl.query.callback}
         * @return a new replacement instance
         */
        public static Replacement varToVar(String matchVariable, String replaceVariable) {
            return new Replacement(new VariableHolder(matchVariable), new VariableHolder(replaceVariable));
        }
    }

    public static final class Builder {

        private int version = 1;
        private List<Mapping> mappings = new ArrayList<>();
        private final List<Replacement> replacements = new ArrayList<>();

        private Builder() {
        }

        public Builder map(Mapping mapping) {
            mappings.add(checkNotNull(mapping));
            return this;
        }

        public Builder replace(Replacement val) {
            replacements.add(checkNotNull(val));
            return this;
        }

        public ServerReplayConfig build() {
            return new ServerReplayConfig(version, mappings, replacements);
        }
    }
}

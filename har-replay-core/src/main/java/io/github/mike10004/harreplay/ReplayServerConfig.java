package io.github.mike10004.harreplay;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral.StringLiteralTypeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Class that represents the configuration that a server replay process uses.
 * You may find this to be a weird way to design a configuration object. The
 * inspiration was the Node {@code har-replay-proxy} configuration object,
 * and this remains compatible with that (mostly) while also (hopefully) being
 * adequate for other replay server implementations.
 *
 * <p>Instances are immutable; invoke {@link #builder()} to get a {@link Builder}.</p>
 */
public class ReplayServerConfig {

    /**
     * Version. Use <code>1</code> for now.
     */
    public final int version;

    /**
     * Maps from URLs to file system paths. Use these to serve matching URLs from the filesystem instead of the HAR.
     */
    @JsonAdapter(ImmutableListTypeAdapterFactory.class)
    public final ImmutableList<Mapping> mappings;

    /**
     * Definitions of replacements to execute for in the body of textual response content.
     */
    @JsonAdapter(ImmutableListTypeAdapterFactory.class)
    public final ImmutableList<Replacement> replacements;

    @JsonAdapter(ImmutableListTypeAdapterFactory.class)
    public final ImmutableList<ResponseHeaderTransform> responseHeaderTransforms;

    private ReplayServerConfig() {
        this(1, ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
    }

    /**
     * Constructs an instance of the class.
     * @param version       the version
     * @param mappings the mappings
     * @param replacements the replacements
     */
    public ReplayServerConfig(int version, Iterable<Mapping> mappings, Iterable<Replacement> replacements, Iterable<ResponseHeaderTransform> responseHeaderTransforms) {
        this.version = version;
        this.mappings = ImmutableList.copyOf(mappings);
        this.replacements = ImmutableList.copyOf(replacements);
        this.responseHeaderTransforms = ImmutableList.copyOf(responseHeaderTransforms);
    }

    /**
     * Constructs a new empty configuration object.
     * @return the configuration object
     */
    @SuppressWarnings("unused")
    public static ReplayServerConfig empty() {
        return new ReplayServerConfig();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Interface for classes that represent the {@code match} field of a {@link Mapping}.
     * Instances of this class represent a strategy to use when determining whether
     * the URL of a request should be responded to with a file mapping.
     */
    public interface MappingMatch {

        /**
         * Evaluates whether a given URL is a match for this instance.
         * @param url the url
         * @return true iff the URL is a match
         */
        boolean evaluateUrlMatch(String url);

    }

    /**
     * Interface for classes that represent the {@code path} field of a {@link Mapping}.
     */
    public interface MappingPath {

        /**
         * Resolves the file to be served when the mapping matches.
         * @param root the root to resolve relative paths against
         * @param match the matching strategy instance
         * @param url the URL that was matched
         * @return the pathname
         */
        File resolveFile(Path root, MappingMatch match, String url);

    }

    /**
     * Interface for classes that represent the {@code match} field of a {@link Replacement}.
     */
    public interface ReplacementMatch {}

    /**
     * Interface for classes that represent the {@code replace} field of a {@link Replacement}.
     */
    public interface ReplacementReplace {

        /**
         * Produces the string that is to be used as replacement text.
         * @param dictionary a dictionary (may not be necessary)
         * @return the interpolated string
         */
        String interpolate(VariableDictionary dictionary);

    }

    /**
     * Parent interface of matching strategies for response header transforms.
     */
    public interface ResponseHeaderTransformMatch {
        Pattern asRegex();
    }

    /**
     * Interface that represents a strategy for matching header names in response header transforms.
     */
    public interface ResponseHeaderTransformNameMatch extends ResponseHeaderTransformMatch {

        boolean isMatchingHeaderName(String headerName);

        static ResponseHeaderTransformNameMatch always() {
            return AlwaysMatch.getInstance();
        }

    }

    /**
     * Interface that represents a strategy for modifying a header name.
     */
    public interface ResponseHeaderTransformNameImage {

        /**
         * Transforms the header name.
         * @param headerName the header name
         * @param nameMatchRegex the name matching regex
         * @return the transformed name, or null if the header is to be removed
         */
        @Nullable
        String transformHeaderName(String headerName, Pattern nameMatchRegex);

        /**
         * Gets an instance that represents the identity transform.
         * @return the identity transform
         */
        static ResponseHeaderTransformNameImage identity() {
            return IdentityImage.getInstance();
        }

    }

    /**
     * Interface that represents a strategy for matching header values in response header transforms.
     */
    public interface ResponseHeaderTransformValueMatch extends ResponseHeaderTransformMatch {

        /**
         * Determines whether a header value is a match according to this instance.
         * @param headerName the header name (in case you want it)
         * @param headerValue the header value to test
         * @return true iff the header value is a match for this instance
         */
        boolean isMatchingHeaderValue(String headerName, String headerValue);

        /**
         * Gets an instance that represents a trivial always-true match.
         * @return the matching strategy instance
         */
        static ResponseHeaderTransformValueMatch always() {
            return AlwaysMatch.getInstance();
        }

    }

    /**
     * Interface that represents a strategy for a response header transform to modify a header value.
     */
    public interface ResponseHeaderTransformValueImage {

        /**
         * Transforms a header value.
         * @param headerName the header name
         * @param valueMatchRegex the regex that determined whether the header was a match
         * @param headerValue the original header value
         * @return the new header value, or null if the header is to be removed from the response
         */
        @Nullable
        String transformHeaderValue(String headerName, Pattern valueMatchRegex, String headerValue);

        /**
         * Gets an instance representing the identity transform. The identity transform does not
         * modify the header.
         * @return the identity image
         */
        static ResponseHeaderTransformValueImage identity() {
            return IdentityImage.getInstance();
        }

    }

    private static final class AlwaysMatch implements ResponseHeaderTransformNameMatch, ResponseHeaderTransformValueMatch {

        private static final AlwaysMatch INSTANCE = new AlwaysMatch();
        private static final Pattern REGEX = Pattern.compile("^(.*)$");

        private AlwaysMatch() {}

        @Override
        public Pattern asRegex() {
            return REGEX;
        }

        @Override
        public boolean isMatchingHeaderName(String headerName) {
            return true;
        }

        @Override
        public boolean isMatchingHeaderValue(String headerName, String headerValue) {
            return true;
        }

        public static AlwaysMatch getInstance() {
            return INSTANCE;
        }

        public String toString() {
            return "ResponseHeaderTransformMatch{ALWAYS}";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AlwaysMatch;
        }
    }

    private static final class IdentityImage implements ResponseHeaderTransformNameImage, ResponseHeaderTransformValueImage{

        private static final IdentityImage INSTANCE = new IdentityImage();

        private IdentityImage() {}

        @Override
        public String transformHeaderName(String headerName, Pattern nameMatchRegex) {
            return headerName;
        }

        @Override
        public String transformHeaderValue(String headerName, Pattern valueMatchRegex, String headerValue) {
            return headerValue;
        }

        public static IdentityImage getInstance() {
            return INSTANCE;
        }

        public String toString() {
            return "ResponseHeaderTransformImage{IDENTITY}";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IdentityImage;
        }
    }

    @VisibleForTesting
    static final class RemoveHeader implements ResponseHeaderTransformNameImage, ResponseHeaderTransformValueImage {

        static final String TYPE_FIELD_VALUE = "RemoveHeader";

        @SuppressWarnings("unused") // used in deserialization
        @SerializedName(CommonDeserializer.TYPE_FIELD_NAME)
        private final String kind = TYPE_FIELD_VALUE;

        private RemoveHeader() {}

        private static final RemoveHeader INSTANCE = new RemoveHeader();

        public static RemoveHeader getInstance() {
            return INSTANCE;
        }

        @Nullable
        @Override
        public String transformHeaderName(String headerName, Pattern nameMatchRegex) {
            return null;
        }

        @Nullable
        @Override
        public String transformHeaderValue(String headerName, Pattern valueMatchRegex, String headerValue) {
            return null;
        }

        public String toString() {
            return "ResponseHeaderTransformImage{REMOVE}";
        }

        @Override
        public int hashCode() {
            return RemoveHeader.class.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RemoveHeader;
        }
    }

    /**
     * Mapping from URL to filesystem pathname. Match field is a string or Javascript regex, and path is a string.
     * Path can contain $n references to substitute capture groups from match.
     */
    @SuppressWarnings("unused")
    public final static class Mapping {

        /**
         * Matching strategy for this mapping.
         */
        public final MappingMatch match;

        /**
         * Pathname of the file to be served if the URL matches this mapping.
         */
        public final MappingPath path;

        /**
         * Constructs a new mapping instance.
         * @param match the matching strategy
         * @param path pathname of the file to be served
         */
        public Mapping(MappingMatch match, MappingPath path) {
            this.match = requireNonNull(match);
            this.path = requireNonNull(path);
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

        /**
         * Constructs a new mapping given an exact match strategy and a pathname
         * @param literalMatch the string that must match the URL exactly
         * @param file the file
         * @return the mapping
         */
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Mapping mapping = (Mapping) o;
            return Objects.equals(match, mapping.match) &&
                    Objects.equals(path, mapping.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(match, path);
        }
    }

    /**
     * Class that represents a string value for a field of a {@link Mapping} or {@link Replacement} instance.
     */
    @com.google.gson.annotations.JsonAdapter(StringLiteralTypeAdapter.class)
    public static class StringLiteral implements MappingMatch, MappingPath,
            ReplacementMatch, ReplacementReplace,
            ResponseHeaderTransformNameImage, ResponseHeaderTransformNameMatch,
            ResponseHeaderTransformValueMatch, ResponseHeaderTransformValueImage {

        public final String value;
        private transient final Supplier<Pattern> regexSupplier;

        private StringLiteral(String value_) {
            this.value = requireNonNull(value_);
            this.regexSupplier = Suppliers.memoize(() -> Pattern.compile("(" + Pattern.quote(value) + ")"));
        }

        /**
         * Returns an instance representing a string.
         * @param value the string value
         * @return the instance
         */
        public static StringLiteral of(String value) {
            return new StringLiteral(value);
        }

        @Override
        public boolean evaluateUrlMatch(String url) {
            return Objects.equals(value, url);
        }

        @Override
        public File resolveFile(Path root, MappingMatch match, String url) {
            File f = new File(value);
            if (f.isAbsolute()) {
                return f;
            } else {
                return root.resolve(f.toPath()).toFile();
            }
        }

        /**
         * Type adapter used in serialization.
         * @see GsonBuilder#registerTypeAdapter(Type, Object)
         */
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

        /**
         * Returns the value held by this instance.
         * @param dictionary a variable dictionary (unused)
         * @return this instance's value, always
         */
        @Override
        public String interpolate(VariableDictionary dictionary) {
            return value;
        }

        @Override
        public boolean isMatchingHeaderName(String headerName) {
            return value.equalsIgnoreCase(headerName);
        }

        @Nullable
        @Override
        public String transformHeaderName(String headerName, Pattern nameMatchRegex) {
            return nameMatchRegex.matcher(headerName).replaceAll(value);
        }

        /**
         * Performs case-sensitive match on the header value.
         * @param headerName the header name
         * @param headerValue the header value
         * @return true iff header value is case sensitive match for this instance's value
         */
        @Override
        public boolean isMatchingHeaderValue(String headerName, String headerValue) {
            return value.equals(headerValue);
        }

        @Nullable
        @Override
        public String transformHeaderValue(String headerName, Pattern valueMatchRegex, String headerValue) {
            Matcher m = valueMatchRegex.matcher(headerValue);
            return m.replaceAll(value);
        }

        @Override
        public Pattern asRegex() {
            return regexSupplier.get();
        }

        @Override
        public String toString() {
            return "StringLiteral{" +
                    "value='" + value + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringLiteral that = (StringLiteral) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    /**
     * Class that represents a variable object. These are used in {@link Replacement}s.
     * See https://github.com/Stuk/server-replay. In a pure-Java implementation
     */
    public static final class VariableHolder implements ReplacementMatch, ReplacementReplace {

        /**
         * Field name that signals to {@link CommonDeserializer} that a given JSON object
         * should be deserialized as an instance of this class.
         */
        static final String SIGNAL_FIELD_NAME = "var";

        /**
         * Variable name.
         */
        public final String var;

        @SuppressWarnings("unused") // for deserialization
        private VariableHolder() {
            var = null;
        }

        private VariableHolder(String var) {
            this.var = requireNonNull(var);
        }

        /**
         * Returns an instance representing a variable.
         * @param var the variable name
         * @return the instance
         */
        public static VariableHolder of(String var) {
            return new VariableHolder(var);
        }

        /**
         * Substitutes
         * @param dictionary the variable lookup dictionary
         * @return the substitution text, or empty string if variable not found
         */
        @Override
        public String interpolate(VariableDictionary dictionary) {
            @Nullable
            Optional<String> substitution = dictionary.substitute(var);
            if (substitution == null) {
                return "";
            }
            return substitution.orElse("");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VariableHolder that = (VariableHolder) o;
            return Objects.equals(var, that.var);
        }

        @Override
        public int hashCode() {
            return Objects.hash(var);
        }
    }

    /**
     * Class that represents a regex object. Instances of this class are used to define {@link Mapping}
     * or {@link Replacement} matchers.
     */
    public static final class RegexHolder implements MappingMatch, ReplacementMatch,
            ResponseHeaderTransformNameMatch, ResponseHeaderTransformValueMatch {

        /**
         * Field name that signals to {@link CommonDeserializer} that a given JSON object
         * should be deserialized as an instance of this class.
         */
        static final String SIGNAL_FIELD_NAME = "regex";
        /**
         * Regex in syntax corresponding to destination engine. If using the node engine,
         * then JavaScript syntax is required. If using the VHS engine, then Java syntax
         * is required.
         */
        public final String regex;
        private transient final Supplier<Pattern> caseSensitivePatternSupplier;
        private transient final Supplier<Pattern> caseInsensitivePatternSupplier;

        @SuppressWarnings("unused") // for deserialization
        private RegexHolder() {
            this("");
        }

        private RegexHolder(String regex_) {
            this.regex = requireNonNull(regex_);
            this.caseSensitivePatternSupplier = Suppliers.memoize(() -> Pattern.compile(regex));
            this.caseInsensitivePatternSupplier = Suppliers.memoize(() -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }

        /**
         * Returns an instance representing a regular expression.
         * @param regex the regular expression
         * @return the instance
         */
        public static RegexHolder of(String regex) {
            return new RegexHolder(regex);
        }

        @Override
        public boolean evaluateUrlMatch(String url) {
            return url != null && url.matches(regex);
        }

        @Override
        public boolean isMatchingHeaderName(String headerName) {
            Matcher matcher = caseInsensitivePatternSupplier.get().matcher(headerName);
            return matcher.find();
        }

        @Override
        public boolean isMatchingHeaderValue(String headerName, String headerValue) {
            Matcher matcher = caseSensitivePatternSupplier.get().matcher(headerValue);
            boolean found = matcher.find();
            return found;
        }

        @Override
        public String toString() {
            return "RegexHolder{" +
                    "regex='" + regex + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegexHolder that = (RegexHolder) o;
            return Objects.equals(regex, that.regex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(regex);
        }

        @Override
        public Pattern asRegex() {
            return caseSensitivePatternSupplier.get();
        }

    }

    /**
     * Replacement strategy for textual response content. {@link #match Match} field is a string,
     * Javascript regex or variable, and {@link #replace} field is a string or variable.
     * The {@code replace} field can contain $n references to substitute capture groups from match.
     */
    public static final class Replacement {

        /**
         * Matching strategy for this replacement.
         */
        public final ReplacementMatch match;

        /**
         * Text substitution strategy for this replacement.
         */
        public final ReplacementReplace replace;

        @SuppressWarnings("unused") // for deserialization
        private Replacement() {
            match = null;
            replace = null;
        }

        /**
         * Constructs a new replacement instance.
         * @param match the matching strategy
         * @param replace the text replacement strategy
         */
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Replacement that = (Replacement) o;
            return Objects.equals(match, that.match) &&
                    Objects.equals(replace, that.replace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(match, replace);
        }
    }

    /**
     * Class that represents a response header transform.
     */
    @SuppressWarnings("unused")
    public static final class ResponseHeaderTransform {

        private final ResponseHeaderTransformNameMatch nameMatch;

        private final ResponseHeaderTransformNameImage nameImage;

        private final ResponseHeaderTransformValueMatch valueMatch;

        private final ResponseHeaderTransformValueImage valueImage;

        private ResponseHeaderTransform(ResponseHeaderTransformNameMatch nameMatch, ResponseHeaderTransformValueMatch valueMatch, ResponseHeaderTransformNameImage nameImage, ResponseHeaderTransformValueImage valueImage) {
            this.nameMatch = nameMatch;
            this.nameImage = nameImage;
            this.valueMatch = valueMatch;
            this.valueImage = valueImage;
        }

        private static ResponseHeaderTransformNameMatch orAlwaysMatch(@Nullable ResponseHeaderTransformNameMatch nameMatch) {
            //noinspection ConstantConditions
            return MoreObjects.firstNonNull(nameMatch, ResponseHeaderTransformNameMatch.always());
        }

        private static ResponseHeaderTransformValueMatch orAlwaysMatch(@Nullable ResponseHeaderTransformValueMatch valueMatch) {
            //noinspection ConstantConditions
            return MoreObjects.firstNonNull(valueMatch, ResponseHeaderTransformValueMatch.always());
        }

        private static ResponseHeaderTransformNameImage orIdentity(@Nullable ResponseHeaderTransformNameImage nameImage) {
            //noinspection ConstantConditions
            return MoreObjects.firstNonNull(nameImage, ResponseHeaderTransformNameImage.identity());
        }

        private static ResponseHeaderTransformValueImage orIdentity(@Nullable ResponseHeaderTransformValueImage valueImage) {
            //noinspection ConstantConditions
            return MoreObjects.firstNonNull(valueImage, ResponseHeaderTransformValueImage.identity());
        }

        /**
         * Gets the header name matching strategy for this instance.
         * @return the name matching strategy
         */
        public ResponseHeaderTransformNameMatch getNameMatch() {
            return orAlwaysMatch(nameMatch);
        }

        /**
         * Gets the header name transform strategy for this instance.
         * @return the name transform strategy
         */
        public ResponseHeaderTransformNameImage getNameImage() {
            return orIdentity(nameImage);
        }

        /**
         * Gets the header value matching strategy for this instance.
         * @return the value matching strategy
         */
        public ResponseHeaderTransformValueMatch getValueMatch() {
            return orAlwaysMatch(valueMatch);
        }

        /**
         * Gets the header value transform strategy for this instance.
         * @return the value transform strategy
         */
        public ResponseHeaderTransformValueImage getValueImage() {
            return orIdentity(valueImage);
        }

        /**
         * Creates a transform that matches based on name and transforms only the header name.
         * @param nameMatch the name matching strategy
         * @param nameImage the name transform strategy
         * @return the transform
         */
        public static ResponseHeaderTransform name(ResponseHeaderTransformNameMatch nameMatch,
                                                   ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), null, requireNonNull(nameImage), null);
        }

        /**
         * Creates a transform that matches based on header value and transforms only the header value.
         * @param valueMatch the value matching strategy
         * @param valueImage the value transform strategy
         * @return the transform
         */
        public static ResponseHeaderTransform value(ResponseHeaderTransformValueMatch valueMatch,
                                                    ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(null, requireNonNull(valueMatch), null, requireNonNull(valueImage));
        }

        /**
         * Creates a transform that matches based on header name and transforms only the header value.
         * @param nameMatch the name matching strategy
         * @param valueImage the value transform strategy
         * @return the transform
         */
        public static ResponseHeaderTransform valueByName(ResponseHeaderTransformNameMatch nameMatch,
                                                          ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), null, null, requireNonNull(valueImage));
        }

        /**
         * Creates a transform that matches based on header value and transforms the header name.
         * @param valueMatch the value matching strategy
         * @param nameImage the name transform strategy
         * @return the transform
         */
        public static ResponseHeaderTransform nameByValue(ResponseHeaderTransformValueMatch valueMatch,
                                                          ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(null, requireNonNull(valueMatch), requireNonNull(nameImage), null);
        }

        /**
         * Creates a transform that matches based on header name and header value and transforms only the
         * header value.
         * @param nameMatch name matching strategy
         * @param valueMatch value matching strategy
         * @param valueImage
         * @return
         */
        public static ResponseHeaderTransform valueByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                  ResponseHeaderTransformValueMatch valueMatch,
                                                                  ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), requireNonNull(valueMatch), null, requireNonNull(valueImage));
        }

        /**
         * Creates a transform that matches based on header name and header value and transforms only
         * the header name.
         * @param nameMatch the name matching strategy
         * @param valueMatch the value matching strategy
         * @param nameImage the name transform strategy
         * @return the transform
         */
        public static ResponseHeaderTransform nameByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                  ResponseHeaderTransformValueMatch valueMatch,
                                                                  ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), requireNonNull(valueMatch), requireNonNull(nameImage), null);
        }

        /**
         * Creates a transform that matches based on header name and header value and transforms
         * both the header name and header value.
         * @param nameMatch the name matching strategy
         * @param valueMatch the value matching strategy
         * @param nameImage the name transform strategy
         * @param valueImage the value transform strategy
         * @return the transform
         */
        public static ResponseHeaderTransform everything(ResponseHeaderTransformNameMatch nameMatch,
                                                           ResponseHeaderTransformValueMatch valueMatch,
                                                           ResponseHeaderTransformNameImage nameImage,
                                                           ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), requireNonNull(valueMatch), requireNonNull(nameImage), requireNonNull(valueImage));
        }

        /**
         * Produces a transform that removes a header, selecting based on the header name.
         * <p>Warning: this is not compatible with the Node engine.
         * @param nameMatch the name matching strategy
         * @return the header transform
         */
        public static ResponseHeaderTransform removeByName(ResponseHeaderTransformNameMatch nameMatch) {
            return new ResponseHeaderTransform(nameMatch, null, RemoveHeader.getInstance(), null);
        }

        /**
         * Produces a transform that removes a header, selecting based on the header name and header value.
         * <p>Warning: this is not compatible with the Node engine.
         * @param nameMatch the name matching strategy
         * @param valueMatch the value matching strategy
         * @return the header transform
         */
        public static ResponseHeaderTransform removeByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                   ResponseHeaderTransformValueMatch valueMatch) {
            return new ResponseHeaderTransform(nameMatch, valueMatch, null, RemoveHeader.getInstance());
        }

        /**
         * Produces a transform that removes a header, selecting based on header value.
         * <p>Warning: this is not compatible with the Node engine.
         * @param valueMatch the value matching strategy
         * @return the header transform
         */
        public static ResponseHeaderTransform removeByValue(ResponseHeaderTransformValueMatch valueMatch) {
            return new ResponseHeaderTransform(null, valueMatch, null, RemoveHeader.getInstance());
        }

        @Override
        public String toString() {
            return "ResponseHeaderTransform{" +
                    "nameMatch=" + nameMatch +
                    ", nameImage=" + nameImage +
                    ", valueMatch=" + valueMatch +
                    ", valueImage=" + valueImage +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ResponseHeaderTransform that = (ResponseHeaderTransform) o;
            return Objects.equals(nameMatch, that.nameMatch) &&
                    Objects.equals(nameImage, that.nameImage) &&
                    Objects.equals(valueMatch, that.valueMatch) &&
                    Objects.equals(valueImage, that.valueImage);
        }

        @Override
        public int hashCode() {

            return Objects.hash(nameMatch, nameImage, valueMatch, valueImage);
        }
    }

    /**
     * Builder of {@code ReplayServerConfig} instances.
     */
    public static final class Builder {

        private int version = 1;
        private final List<Mapping> mappings = new ArrayList<>();
        private final List<Replacement> replacements = new ArrayList<>();
        private final List<ResponseHeaderTransform> responseHeaderTransforms = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a mapping.
         * @param mapping the mapping
         * @return this builder instance
         */
        public Builder map(Mapping mapping) {
            mappings.add(requireNonNull(mapping));
            return this;
        }

        /**
         * Adds a replacement.
         * @param val the replacement
         * @return this builder instance
         */
        public Builder replace(Replacement val) {
            replacements.add(requireNonNull(val));
            return this;
        }

        /**
         * Adds a response header transform.
         * @param responseHeaderTransform the transform
         * @return this builder instance
         */
        public Builder transformResponse(ResponseHeaderTransform responseHeaderTransform) {
            responseHeaderTransforms.add(requireNonNull(responseHeaderTransform));
            return this;
        }

        /**
         * Builds the config instance.
         * @return the immutable config instance
         */
        public ReplayServerConfig build() {
            return new ReplayServerConfig(version, mappings, replacements, responseHeaderTransforms);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplayServerConfig that = (ReplayServerConfig) o;
        return version == that.version &&
                Objects.equals(mappings, that.mappings) &&
                Objects.equals(replacements, that.replacements) &&
                Objects.equals(responseHeaderTransforms, that.responseHeaderTransforms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, mappings, replacements, responseHeaderTransforms);
    }

    /**
     * Deserializer for concrete classes that implement the set of interfaces
     * contained in {@link #INTERFACES_HANDLED_BY_COMMON_DESERIALIZER}. Currently deserializes
     * JSON to instances of {@link StringLiteral}, {@link RegexHolder}, {@link RemoveHeader},
     * and {@link VariableHolder}.
     */
    @VisibleForTesting
    static class CommonDeserializer implements JsonDeserializer<Object> {

        private static final Logger log = LoggerFactory.getLogger(CommonDeserializer.class);

        private static final CommonDeserializer INSTANCE = new CommonDeserializer();

        public static final String TYPE_FIELD_NAME = "#kind";

        private final Gson stockGson = new Gson();

        CommonDeserializer() {
        }

        public static JsonDeserializer<Object> getInstance() {
            return INSTANCE;
        }

        protected <E> E deserializeAs(JsonElement element, Class<E> type) {
            return stockGson.fromJson(element, type);
        }

        @Override
        public Object deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return StringLiteral.of(json.getAsString());
            }
            if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                JsonElement typeFieldValue = obj.get(TYPE_FIELD_NAME);
                if (typeFieldValue != null) {
                    String kind = typeFieldValue.getAsString();
                    if (Strings.isNullOrEmpty(kind)) {
                        throw new JsonParseException(TYPE_FIELD_NAME + " field value must be nonempty");
                    }
                    switch (kind) {
                        case RemoveHeader.TYPE_FIELD_VALUE:
                            return RemoveHeader.getInstance();
                        default:
                            log.warn("unrecognized type field value: {}", kind);
                    }
                }
                JsonElement regexFieldValue = obj.get(RegexHolder.SIGNAL_FIELD_NAME);
                if (regexFieldValue != null) {
                    return deserializeAs(obj, RegexHolder.class);
                }
                JsonElement varFieldValue = obj.get(VariableHolder.SIGNAL_FIELD_NAME);
                if (varFieldValue != null) {
                    return deserializeAs(obj, VariableHolder.class);
                }
            }
            throw new JsonParseException("could not determine type of " + json);
        }
    }

    @VisibleForTesting
    static final ImmutableSet<Class<?>> INTERFACES_HANDLED_BY_COMMON_DESERIALIZER = ImmutableSet.<Class<?>>builder()
            .add(MappingMatch.class)
            .add(MappingPath.class)
            .add(ReplacementMatch.class)
            .add(ReplacementReplace.class)
            .add(ResponseHeaderTransformNameMatch.class)
            .add(ResponseHeaderTransformNameImage.class)
            .add(ResponseHeaderTransformValueMatch.class)
            .add(ResponseHeaderTransformValueImage.class)
            .build();

    /**
     * Constructs and returns a Gson instance capable of deserializing instances of this class.
     * @return a new gson instance
     */
    public static Gson createSerialist() {
        JsonDeserializer<?> commonDeserializer = CommonDeserializer.getInstance();
        GsonBuilder b = new GsonBuilder().setPrettyPrinting();
        for (Class<?> interface_ : INTERFACES_HANDLED_BY_COMMON_DESERIALIZER) {
            b.registerTypeHierarchyAdapter(interface_, commonDeserializer);
        }
        return b.create();
    }

}

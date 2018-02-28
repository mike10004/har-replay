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
 * and this remains compatible with that while also (hopefully) being adequate
 * for other replay server implementations.
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
     */
    public interface MappingMatch {

        boolean evaluateUrlMatch(String url);

    }

    /**
     * Interface for classes that represent the {@code path} field of a {@link Mapping}.
     */
    public interface MappingPath {

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

        String interpolate(VariableDictionary dictionary);

    }

    public interface ResponseHeaderTransformNameMatch {

        boolean isMatchingHeaderName(String headerName);

        static ResponseHeaderTransformNameMatch always() {
            return AlwaysMatch.getInstance();
        }

        Pattern asRegex();

    }

    public interface ResponseHeaderTransformNameImage {

        @Nullable
        String transformHeaderName(String headerName, Pattern nameMatchRegex);

        static ResponseHeaderTransformNameImage identity() {
            return IdentityImage.getInstance();
        }

    }

    public interface ResponseHeaderTransformValueMatch {

        boolean isMatchingHeaderValue(String headerName, String headerValue);

        Pattern asRegex();

        static ResponseHeaderTransformValueMatch always() {
            return AlwaysMatch.getInstance();
        }

    }

    public interface ResponseHeaderTransformValueImage {

        @Nullable
        String transformHeaderValue(String headerName, Pattern valueMatchRegex, String headerValue);

        static ResponseHeaderTransformValueImage identity() {
            return IdentityImage.getInstance();
        }

    }

    static final class AlwaysMatch implements ResponseHeaderTransformNameMatch, ResponseHeaderTransformValueMatch {

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

    public static final class RemoveHeader implements ResponseHeaderTransformNameImage, ResponseHeaderTransformValueImage {

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

        public final MappingMatch match;
        public final MappingPath path;

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

        public ResponseHeaderTransformNameMatch getNameMatch() {
            return orAlwaysMatch(nameMatch);
        }

        public ResponseHeaderTransformNameImage getNameImage() {
            return orIdentity(nameImage);
        }

        public ResponseHeaderTransformValueMatch getValueMatch() {
            return orAlwaysMatch(valueMatch);
        }

        public ResponseHeaderTransformValueImage getValueImage() {
            return orIdentity(valueImage);
        }

        public static ResponseHeaderTransform name(ResponseHeaderTransformNameMatch nameMatch,
                                                   ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), null, requireNonNull(nameImage), null);
        }

        public static ResponseHeaderTransform value(ResponseHeaderTransformValueMatch valueMatch,
                                                    ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(null, requireNonNull(valueMatch), null, requireNonNull(valueImage));
        }

        public static ResponseHeaderTransform valueByName(ResponseHeaderTransformNameMatch nameMatch,
                                                          ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), null, null, requireNonNull(valueImage));
        }

        public static ResponseHeaderTransform nameByValue(ResponseHeaderTransformValueMatch valueMatch,
                                                          ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(null, requireNonNull(valueMatch), requireNonNull(nameImage), null);
        }

        public static ResponseHeaderTransform valueByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                  ResponseHeaderTransformValueMatch valueMatch,
                                                                  ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), requireNonNull(valueMatch), null, requireNonNull(valueImage));
        }

        public static ResponseHeaderTransform nameByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                  ResponseHeaderTransformValueMatch valueMatch,
                                                                  ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), requireNonNull(valueMatch), requireNonNull(nameImage), null);
        }

        public static ResponseHeaderTransform everything(ResponseHeaderTransformNameMatch nameMatch,
                                                           ResponseHeaderTransformValueMatch valueMatch,
                                                           ResponseHeaderTransformNameImage nameImage,
                                                           ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(requireNonNull(nameMatch), requireNonNull(valueMatch), requireNonNull(nameImage), requireNonNull(valueImage));
        }

        public static ResponseHeaderTransform removeByName(ResponseHeaderTransformNameMatch nameMatch) {
            return new ResponseHeaderTransform(nameMatch, null, RemoveHeader.getInstance(), null);
        }

        public static ResponseHeaderTransform removeByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                   ResponseHeaderTransformValueMatch valueMatch) {
            return new ResponseHeaderTransform(nameMatch, valueMatch, null, RemoveHeader.getInstance());
        }

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

    public static final class Builder {

        private int version = 1;
        private final List<Mapping> mappings = new ArrayList<>();
        private final List<Replacement> replacements = new ArrayList<>();
        private final List<ResponseHeaderTransform> responseHeaderTransforms = new ArrayList<>();
        private Builder() {
        }

        public Builder map(Mapping mapping) {
            mappings.add(requireNonNull(mapping));
            return this;
        }

        public Builder replace(Replacement val) {
            replacements.add(requireNonNull(val));
            return this;
        }

        public Builder transformResponse(ResponseHeaderTransform responseHeaderTransform) {
            responseHeaderTransforms.add(requireNonNull(responseHeaderTransform));
            return this;
        }

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
    
    public static Gson createSerialist() {
        JsonDeserializer<?> commonDeserializer = CommonDeserializer.getInstance();
        GsonBuilder b = new GsonBuilder().setPrettyPrinting();
        for (Class<?> interface_ : INTERFACES_HANDLED_BY_COMMON_DESERIALIZER) {
            b.registerTypeHierarchyAdapter(interface_, commonDeserializer);
        }
        return b.create();
    }

}

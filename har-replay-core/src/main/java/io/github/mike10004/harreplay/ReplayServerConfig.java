package io.github.mike10004.harreplay;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral.StringLiteralTypeAdapter;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Class that represents the configuration that a server replay process uses. This object's structure
 * matches the structure of the object that the Node har-replay-proxy module uses. See that project's
 * documentation for information on the various parameters.
 *
 * <p>This class is not currently deserializable, but it serializes to the JSON format required
 * by the har-replay-proxy Node module just fine.
 * </p>
 */
public class ReplayServerConfig {

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

        String interpolate(String url);

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

    private static class AlwaysMatch implements ResponseHeaderTransformNameMatch, ResponseHeaderTransformValueMatch {

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
    }

    private static class IdentityImage implements ResponseHeaderTransformNameImage, ResponseHeaderTransformValueImage{

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
    }

    public static class RemoveHeader implements ResponseHeaderTransformNameImage, ResponseHeaderTransformValueImage {

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
    public static class StringLiteral implements MappingMatch, MappingPath,
            ReplacementMatch, ReplacementReplace,
            ResponseHeaderTransformNameImage, ResponseHeaderTransformNameMatch,
            ResponseHeaderTransformValueMatch, ResponseHeaderTransformValueImage {

        public final String value;
        private transient final Pattern regex;

        private StringLiteral(String value) {
            this.value = requireNonNull(value);
            this.regex = Pattern.compile("(" + Pattern.quote(value) + ")");
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

        @Override
        public String interpolate(String url) {
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
            return regex;
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
     * See https://github.com/Stuk/server-replay.
     */
    public static final class VariableHolder implements ReplacementMatch, ReplacementReplace {

        public final String var;

        @SuppressWarnings("unused") // for deserialization
        private VariableHolder() {
            var = null;
        }

        private VariableHolder(String var) {
            this.var = checkNotNull(var);
        }

        public static VariableHolder of(String var) {
            return new VariableHolder(var);
        }

        @Override
        public String interpolate(String url) {
            if ("request.url".equals(var)) {
                return url;
            }
            LoggerFactory.getLogger(getClass()).info("unresolved variable {}", var);
            return var;
        }
    }

    /**
     * Class that represents a regex object. Instances of this class are used to define {@link Mapping}
     * or {@link Replacement} matchers.
     */
    public static final class RegexHolder implements MappingMatch, ReplacementMatch,
            ResponseHeaderTransformNameMatch, ResponseHeaderTransformValueMatch {

        /**
         * Regex in Javascript syntax.
         */
        public final String regex;
        private transient final Pattern caseSensitivePattern;
        private transient final Pattern caseInsensitivePattern;

        @SuppressWarnings("unused") // for deserialization
        private RegexHolder() {
            regex = null;
            caseSensitivePattern = null;
            caseInsensitivePattern = null;
        }

        private RegexHolder(String regex) {
            this.regex = requireNonNull(regex);
            this.caseSensitivePattern = Pattern.compile(regex);
            this.caseInsensitivePattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
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
            Matcher matcher = caseInsensitivePattern.matcher(headerName);
            return matcher.find();
        }

        @Override
        public boolean isMatchingHeaderValue(String headerName, String headerValue) {
            Matcher matcher = caseSensitivePattern.matcher(headerValue);
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
            return caseSensitivePattern;
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
            return new ResponseHeaderTransform(checkNotNull(nameMatch), null, checkNotNull(nameImage), null);
        }

        public static ResponseHeaderTransform value(ResponseHeaderTransformValueMatch valueMatch,
                                                    ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(null, checkNotNull(valueMatch), null, checkNotNull(valueImage));
        }

        public static ResponseHeaderTransform valueByName(ResponseHeaderTransformNameMatch nameMatch,
                                                          ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(checkNotNull(nameMatch), null, null, checkNotNull(valueImage));
        }

        public static ResponseHeaderTransform nameByValue(ResponseHeaderTransformValueMatch valueMatch,
                                                          ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(null, checkNotNull(valueMatch), checkNotNull(nameImage), null);
        }

        public static ResponseHeaderTransform valueByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                  ResponseHeaderTransformValueMatch valueMatch,
                                                                  ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(checkNotNull(nameMatch), checkNotNull(valueMatch), null, checkNotNull(valueImage));
        }

        public static ResponseHeaderTransform nameByNameAndValue(ResponseHeaderTransformNameMatch nameMatch,
                                                                  ResponseHeaderTransformValueMatch valueMatch,
                                                                  ResponseHeaderTransformNameImage nameImage) {
            return new ResponseHeaderTransform(checkNotNull(nameMatch), checkNotNull(valueMatch), checkNotNull(nameImage), null);
        }

        public static ResponseHeaderTransform everything(ResponseHeaderTransformNameMatch nameMatch,
                                                           ResponseHeaderTransformValueMatch valueMatch,
                                                           ResponseHeaderTransformNameImage nameImage,
                                                           ResponseHeaderTransformValueImage valueImage) {
            return new ResponseHeaderTransform(checkNotNull(nameMatch), checkNotNull(valueMatch), checkNotNull(nameImage), checkNotNull(valueImage));
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
    }

    public static final class Builder {

        private int version = 1;
        private final List<Mapping> mappings = new ArrayList<>();
        private final List<Replacement> replacements = new ArrayList<>();
        private final List<ResponseHeaderTransform> responseHeaderTransforms = new ArrayList<>();
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

        public Builder transformResponse(ResponseHeaderTransform responseHeaderTransform) {
            responseHeaderTransforms.add(checkNotNull(responseHeaderTransform));
            return this;
        }

        public ReplayServerConfig build() {
            return new ReplayServerConfig(version, mappings, replacements, responseHeaderTransforms);
        }
    }
}

package com.github.mike10004.harreplay;

import com.github.mike10004.harreplay.ServerReplayConfig.StringHolder.StringHolderTypeAdapter;
import com.google.common.collect.ImmutableList;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServerReplayConfig {

    public final int version;
    public final ImmutableList<Mapping> mappings;
    public final ImmutableList<Replacement> replacements;

    private ServerReplayConfig() {
        this(1, ImmutableList.of(), ImmutableList.of());
    }

    public static ServerReplayConfig empty() {
        return new ServerReplayConfig();
    }

    public static ServerReplayConfig basic() {
        return new ServerReplayConfig(1, ImmutableList.of(), ImmutableList.of(Replacement.literal("https", "http")));
    }

    public ServerReplayConfig(int version, ImmutableList<Mapping> mappings, ImmutableList<Replacement> replacements) {
        this.version = version;
        this.mappings = checkNotNull(mappings);
        this.replacements = checkNotNull(replacements);
    }

    public interface MappingMatch {}

    public interface ReplacementMatch {}
    public interface ReplacementReplace {}

    public abstract static class Mapping<M extends MappingMatch> {

        public final M match;
        public final String path;

        protected Mapping(M match, String path) {
            this.match = checkNotNull(match);
            this.path = checkNotNull(path);
        }
    }

    @JsonAdapter(StringHolderTypeAdapter.class)
    public static class StringHolder implements MappingMatch, ReplacementMatch, ReplacementReplace {

        public final String value;

        @SuppressWarnings("unused") // for deserialization
        private StringHolder() {
            value = null;
        }

        public StringHolder(String value) {
            this.value = checkNotNull(value);
        }

        public static class StringHolderTypeAdapter extends TypeAdapter<StringHolder> {

            @Override
            public void write(JsonWriter out, StringHolder value) throws IOException {
                out.value(value.value);
            }

            @Override
            public StringHolder read(JsonReader in) throws IOException {
                String value = in.nextString();
                return new StringHolder(value);
            }
        }
    }

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

    public static class RegexHolder implements MappingMatch, ReplacementMatch {

        public final String regex;

        @SuppressWarnings("unused") // for deserialization
        private RegexHolder() {
            regex = null;
        }

        public RegexHolder(String regex) {
            this.regex = checkNotNull(regex);
        }
    }

    public static class Replacement<M extends ReplacementMatch, R extends ReplacementReplace> {

        public final M match;
        public final R replace;

        @SuppressWarnings("unused") // for deserialization
        private Replacement() {
            match = null;
            replace = null;
        }

        public Replacement(M match, R replace) {
            this.match = match;
            this.replace = replace;
        }

        public static Replacement literal(String match, String replace) {
            return new Replacement<>(new StringHolder(match), new StringHolder(replace));
        }

        public static Replacement regexToString(String regex, String replace) {
            return new Replacement<>(new RegexHolder(regex), new StringHolder(replace));
        }
    }

    public static abstract class HolderTypeAdapter<T> extends TypeAdapter<T> {

        @Override
        public void write(JsonWriter out, T holder) throws IOException {
            out.value(getHeldValue(holder));
        }

        @Override
        public T read(JsonReader in) throws IOException {
            String value = in.nextString();
            T holder = construct(value);
            return holder;
        }

        protected abstract T construct(String heldValue);
        protected abstract String getHeldValue(T holder);
    }

}

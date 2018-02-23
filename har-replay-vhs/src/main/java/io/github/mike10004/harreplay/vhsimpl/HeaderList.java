package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HeaderList {

    private final ImmutableList<Entry<String, String>> headers;

    public HeaderList(Iterable<Entry<String, String>> headers) {
        this.headers = ImmutableList.copyOf(headers);
    }

    @Nullable
    public String getValue(String name) {
        requireNonNull(name, "name");
        return headers.stream().filter(e -> name.equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    public static HeaderList from(Stream<? extends Entry<String, String>> stream) {
        return new HeaderList(stream.collect(Collectors.toList()));
    }
}

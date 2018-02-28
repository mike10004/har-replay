package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class NameValuePairList<T> {

    private final ImmutableList<T> pairs;
    private final Function<? super T, String> nameGetter;
    private final Function<? super T, String> valueGetter;
    private final BiPredicate<? super String, ? super String> nameMatcher;

    public NameValuePairList(Iterable<T> pairs, Function<? super T, String> nameGetter, Function<? super T, String> valueGetter, BiPredicate<? super String, ? super String> nameMatcher) {
        this.pairs = ImmutableList.copyOf(pairs);
        this.nameGetter = requireNonNull(nameGetter);
        this.valueGetter = requireNonNull(valueGetter);
        this.nameMatcher = requireNonNull(nameMatcher);
    }

    private static final BiPredicate<String, String> CASE_INSENSITIVE_MATCHER = String::equalsIgnoreCase;
    private static final BiPredicate<String, String> CASE_SENSITIVE_MATCHER = String::equals;

    protected static BiPredicate<String, String> caseSensitiveMatcher() {
        return CASE_SENSITIVE_MATCHER;
    }

    protected static BiPredicate<String, String> caseInsensitiveMatcher() {
        return CASE_INSENSITIVE_MATCHER;
    }

    @Nullable
    public String getFirstValue(String name) {
        requireNonNull(name, "name");
        return pairs.stream()
                .filter(p -> nameMatcher.test(name, nameGetter.apply(p)))
                .map(valueGetter::apply)
                .findFirst()
                .orElse(null);
    }

    public Stream<String> streamValues(String name) {
        requireNonNull(name, "name");
        return pairs.stream()
                .filter(p -> nameMatcher.test(name, nameGetter.apply(p)))
                .map(valueGetter::apply);
    }

    public ImmutableList<String> listValues(String name) {
        return streamValues(name).collect(ImmutableList.toImmutableList());
    }

    public static <T> NameValuePairList<T> caseSensitive(Iterable<T> pairs, Function<? super T, String> nameGetter, Function<? super T, String> valueGetter) {
        return new NameValuePairList<T>(pairs, nameGetter, valueGetter, CASE_SENSITIVE_MATCHER);
    }

    public static <T> NameValuePairList<T> caseInsensitive(Iterable<T> pairs, Function<? super T, String> nameGetter, Function<? super T, String> valueGetter) {
        return new NameValuePairList<T>(pairs, nameGetter, valueGetter, CASE_INSENSITIVE_MATCHER);
    }
}

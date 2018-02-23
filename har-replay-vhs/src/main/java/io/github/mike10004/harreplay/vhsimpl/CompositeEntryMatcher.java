package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ParsedRequest;

import javax.annotation.Nullable;

public class CompositeEntryMatcher implements EntryMatcher {

    private final ImmutableList<EntryMatcher> components;

    public CompositeEntryMatcher(Iterable<EntryMatcher> components) {
        this.components = ImmutableList.copyOf(components);
    }

    @Nullable
    @Override
    public HttpRespondable findTopEntry(ParsedRequest request) {
        for (EntryMatcher component : components) {
            HttpRespondable respondable = component.findTopEntry(request);
            if (respondable != null) {
                return respondable;
            }
        }
        return null;
    }

}

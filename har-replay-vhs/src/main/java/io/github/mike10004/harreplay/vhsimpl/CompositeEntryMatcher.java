package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

import javax.annotation.Nullable;

public class CompositeEntryMatcher<S> implements EntryMatcher<S> {

    private final ImmutableList<EntryMatcher<? super S>> components;

    public CompositeEntryMatcher(Iterable<EntryMatcher<? super S>> components) {
        this.components = ImmutableList.copyOf(components);
    }

    @Nullable
    @Override
    public HttpRespondable findTopEntry(S state, ParsedRequest request) {
        for (EntryMatcher<? super S> component : components) {
            HttpRespondable respondable = component.findTopEntry(state, request);
            if (respondable != null) {
                return respondable;
            }
        }
        return null;
    }

}

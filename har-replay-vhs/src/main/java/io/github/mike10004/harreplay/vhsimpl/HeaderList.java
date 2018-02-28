package io.github.mike10004.harreplay.vhsimpl;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class HeaderList extends NameValuePairList<Map.Entry<String, String>> {

    private HeaderList(Iterable<Map.Entry<String, String>> headers) {
        super(headers, Map.Entry::getKey, Map.Entry::getValue, caseInsensitiveMatcher());
    }

    public static HeaderList from(Stream<? extends Map.Entry<String, String>> stream) {
        return new HeaderList(stream.collect(Collectors.toList()));
    }

}

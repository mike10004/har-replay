package io.github.mike10004.harreplay.tests;

import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface Grep {

    interface Match {
        int lineNumber();
        String line();
    }

    Stream<Match> filter(CharSource charSource) throws IOException;

    static Grep byPattern(Pattern pattern) {
        AtomicInteger counter = new AtomicInteger(0);
        Function<String, Match> matchFunction = line -> {
            return new Match() {

                private final int lineNumber;

                {
                    lineNumber = counter.incrementAndGet();
                }

                @Override
                public int lineNumber() {
                    return lineNumber;
                }

                @Override
                public String line() {
                    return line;
                }
            };
        };
        return new Grep() {
            @Override
            public Stream<Match> filter(CharSource charSource) throws IOException {
                return charSource.lines()
                        .map(matchFunction)
                        .filter(match -> {
                    return pattern.matcher(match.line()).find();
                });
            }
        };
    }



}

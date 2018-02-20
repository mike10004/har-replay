package io.github.mike10004.harreplay.exec;

import com.google.common.io.CharSource;

import java.io.IOException;

public interface HarTransform {

    CharSource transform(CharSource originalHar) throws IOException;

}

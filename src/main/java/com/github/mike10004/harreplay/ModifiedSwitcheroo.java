package com.github.mike10004.harreplay;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

public class ModifiedSwitcheroo {

    private ModifiedSwitcheroo() {}

    public static ByteSource getExtensionCrxByteSource() {
        return Resources.asByteSource(ModifiedSwitcheroo.class.getResource("/modified-switcheroo.crx"));
    }
}

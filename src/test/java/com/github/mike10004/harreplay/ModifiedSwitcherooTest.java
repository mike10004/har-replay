package com.github.mike10004.harreplay;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;

public class ModifiedSwitcherooTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void getExtensionCrxByteSource() throws Exception {
        ByteSource bs = ModifiedSwitcheroo.getExtensionCrxByteSource();
        File outfile = temporaryFolder.newFile();
        bs.copyTo(Files.asByteSink(outfile));
        assertTrue("outfile nonempty", outfile.length() > 0);
    }

}
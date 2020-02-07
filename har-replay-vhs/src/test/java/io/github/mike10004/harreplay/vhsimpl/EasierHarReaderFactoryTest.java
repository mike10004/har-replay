package io.github.mike10004.harreplay.vhsimpl;

import com.browserup.harreader.HarReaderMode;
import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import io.github.mike10004.harreplay.tests.Fixtures;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;

public class EasierHarReaderFactoryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readHarGeneratedByBrowsermob() throws Exception {
        File harFile = Fixtures.copyBrowsermobGeneratedHarFile(temporaryFolder.getRoot().toPath());
        Har har = new EasierHarReaderFactory().createReader().readFromFile(harFile, HarReaderMode.STRICT);
        HarEntry entryWithBadDate = har.getLog().getEntries().iterator().next();
        assertNotNull("date with bad format", entryWithBadDate.getStartedDateTime());
        assertNotEquals("date to epoch millis", 0L, entryWithBadDate.getStartedDateTime().getTime());
    }

}
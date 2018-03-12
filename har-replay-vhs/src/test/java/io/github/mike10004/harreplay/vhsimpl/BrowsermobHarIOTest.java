package io.github.mike10004.harreplay.vhsimpl;

import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.harreplay.tests.Fixtures;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BrowsermobHarIOTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readHarGeneratedByBrowsermob() throws Exception {
        File harFile = Fixtures.copyBrowsermobGeneratedHarFile(temporaryFolder.getRoot().toPath());
        Har har = HarReaderFactory.easier().createReader().readFromFile(harFile, HarReaderMode.STRICT);
        HarEntry entryWithBadDate = har.getLog().getEntries().iterator().next();
        assertNotNull("date with bad format", entryWithBadDate.getStartedDateTime());
        assertNotEquals("date to epoch millis", 0L, entryWithBadDate.getStartedDateTime().getTime());
    }

    @Test
    public void readHarGeneratedByBrowsermob_stockHarReaderFactoryFails() throws Exception {
        File harFile = Fixtures.copyBrowsermobGeneratedHarFile(temporaryFolder.getRoot().toPath());
        try {
            HarReaderFactory.stock().createReader().readFromFile(harFile, HarReaderMode.STRICT);
            fail("no exception thrown");
        } catch (HarReaderException e) {
            assertTrue("caused by invalid date format", e.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException);
        }
    }

}

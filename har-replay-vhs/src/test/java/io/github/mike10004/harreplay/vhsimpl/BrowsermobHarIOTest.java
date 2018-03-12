package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BrowsermobHarIOTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readHarGeneratedByBrowsermob() throws Exception {
        URL harResource = getClass().getResource("/browsermob-generated.har");
        File harFile = temporaryFolder.newFile();
        Resources.asByteSource(harResource).copyTo(Files.asByteSink(harFile));
        try {
            new HarReader().readFromFile(harFile, HarReaderMode.STRICT);
            fail("no exception thrown");
        } catch (HarReaderException e) {
            assertTrue("caused by invalid date format", e.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException);
        }

        Har har = HarReaderFactory.easier().createReader().readFromFile(harFile, HarReaderMode.STRICT);
        HarEntry entryWithBadDate = har.getLog().getEntries().iterator().next();
        assertNotNull("date with bad format", entryWithBadDate.getStartedDateTime());
        assertNotEquals("date to epoch millis", 0L, entryWithBadDate.getStartedDateTime().getTime());
    }
}

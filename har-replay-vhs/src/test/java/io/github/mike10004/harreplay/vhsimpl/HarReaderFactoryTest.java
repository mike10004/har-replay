package io.github.mike10004.harreplay.vhsimpl;

import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import io.github.mike10004.harreplay.tests.Fixtures;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HarReaderFactoryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stockHarReaderFactoryFailsOnBrowsermobHar() throws Exception {
        File harFile = Fixtures.copyBrowsermobGeneratedHarFile(temporaryFolder.getRoot().toPath());
        try {
            HarReaderFactory.stock().createReader().readFromFile(harFile, HarReaderMode.STRICT);
            fail("no exception thrown");
        } catch (HarReaderException e) {
            assertTrue("caused by invalid date format", e.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException);
        }
    }

}

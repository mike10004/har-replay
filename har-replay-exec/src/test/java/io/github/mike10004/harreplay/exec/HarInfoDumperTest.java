package io.github.mike10004.harreplay.exec;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.harreplay.exec.HarInfoDumper.SummaryDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.TerseDumper;
import io.github.mike10004.harreplay.exec.HarInfoDumper.VerboseDumper;
import io.github.mike10004.harreplay.tests.Fixtures;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class HarInfoDumperTest {

    @ClassRule
    public static FixturesRule fixturesRule = Fixtures.asRule();

    @Test
    public void summary() throws Exception {
        String output = dump(new SummaryDumper());
        System.out.println(output);
    }

    @Test
    public void terse() throws Exception {
        String output = dump(new TerseDumper());
        System.out.println(output);
    }

    @Test
    public void verbose() throws Exception {
        String output = dump(new VerboseDumper());
        System.out.println(output);
    }


    private String dump(HarInfoDumper dumper) throws UnsupportedEncodingException, HarReaderException {
        Charset charset = StandardCharsets.UTF_8;
        System.out.format("%s%n", dumper.getClass().getSimpleName());
        Fixtures f = fixturesRule.getFixtures();
        File harFile = f.http().harFile();
        List<HarEntry> entries = new HarReader().readFromFile(harFile).getLog().getEntries();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        try (PrintStream out = new PrintStream(baos, true, charset.name())) {
            dumper.dump(entries, out);
        }
        return baos.toString(charset.name());
    }
}
package io.github.mike10004.harreplay.exec;

import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.gson.JsonObject;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.model.Har;
import io.github.mike10004.harreplay.tests.Fixtures;
import io.github.mike10004.harreplay.tests.Fixtures.FixturesRule;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.Assert.*;

public class SstoehrHarCleaningTransformTest {

    @Test
    public void transformLightbodyHar() throws Exception {
        File lightbodyHarFile = new File(getClass().getResource("/example-lightbody.har").toURI());
        CharSource originalHar = Files.asCharSource(lightbodyHarFile, StandardCharsets.UTF_8);
        String transformed = SstoehrHarCleaningTransform.inMemory().transform(originalHar).read();
        Har har = new HarReader().readFromString(transformed);
        Date date = har.getLog().getPages().get(0).getStartedDateTime();
        System.out.format("date: %s%n", date);
        assertNotNull("date", date);
    }

    @Test
    public void cleanDate()  throws Exception {
        String input = "Feb 16, 2018 4:41:27 PM";
        String output = SstoehrHarCleaningTransform.cleanDate(input);
        System.out.format("%s -> %s%n", input, output);
        assertNotEquals("cleaned date", input, output);
    }

    @Test
    public void cleanDateChild()  throws Exception {
        String dateStr = "Feb 16, 2018 4:41:27 PM";
        JsonObject personObj = new JsonObject();
        String fieldName = "birthday";
        personObj.addProperty(fieldName, dateStr);
        SstoehrHarCleaningTransform.cleanDateChild(personObj, fieldName);
        String output = personObj.getAsJsonPrimitive(fieldName).getAsString();
        System.out.format("%s -> %s%n", dateStr, output);
        assertNotEquals("cleaned date", dateStr, output);
    }
}
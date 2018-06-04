package io.github.mike10004.vhs.harbridge;

import org.junit.Test;

import static org.junit.Assert.*;

public class ContentDispositionTest {

    @Test
    public void parse() {
        String header = "form-data; name=\"f\"; filename=\"image-for-upload.jpeg\"";
        ContentDisposition d = ContentDisposition.parse(header);
        System.out.format("parsed: %s%n", d);
        assertEquals("type", "form-data", d.getType());
        assertEquals("name", "f", d.getName());
        assertEquals("filename", "image-for-upload.jpeg", d.getFilename());
    }
}
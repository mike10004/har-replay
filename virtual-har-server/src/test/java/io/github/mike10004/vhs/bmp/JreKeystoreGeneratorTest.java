package io.github.mike10004.vhs.bmp;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class JreKeystoreGeneratorTest {

    @Test
    public void asciiBytesToChars() throws Exception {

        String asciiCharStr = "abcdefghijklmnop\n\t\r1234567890!@#$%^&*()`\'\"<>?:{}[];,./";
        byte[] asciiBytes = asciiCharStr.getBytes(StandardCharsets.US_ASCII);
        char[] asciiChars = JreKeystoreGenerator.asciiBytesToChars(asciiBytes);
        assertArrayEquals("chars -> bytes -> chars", asciiCharStr.toCharArray(), asciiChars);


    }


}
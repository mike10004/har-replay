package com.github.mike10004.harreplay;

import com.google.gson.Gson;
import net.lightbody.bmp.core.har.Har;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class HarIO {

    private HarIO() {}

    public static Har fromFile(File harFile) throws IOException {
        Har har;
        try (Reader reader = new FileReader(harFile)) {
            har = new Gson().fromJson(reader, Har.class);
        }
        return har;
    }
}

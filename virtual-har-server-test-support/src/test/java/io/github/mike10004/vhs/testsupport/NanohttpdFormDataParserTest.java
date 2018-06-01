package io.github.mike10004.vhs.testsupport;

import io.github.mike10004.vhs.harbridge.FormDataPart;
import io.github.mike10004.vhs.harbridge.MultipartFormDataParser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class NanohttpdFormDataParserTest extends MultipartFormDataParserTestBase {

    private static final Charset FORM_DATA_URLENCODED_DEFAULT_ENCODING = StandardCharsets.UTF_8;

    @Override
    protected MultipartFormDataParser createParser() {
        return new NanohttpdFormDataParser();
    }

    @Override
    protected String decodeParamPartData(FormDataPart paramPart) throws IOException {
        if (paramPart.file == null) {
            return "";
        }
        return paramPart.file.asByteSource().asCharSource(FORM_DATA_URLENCODED_DEFAULT_ENCODING).read();
    }
}
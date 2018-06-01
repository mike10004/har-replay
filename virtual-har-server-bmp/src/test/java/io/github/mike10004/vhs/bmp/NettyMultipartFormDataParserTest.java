package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.harbridge.FormDataPart;
import io.github.mike10004.vhs.harbridge.MultipartFormDataParser;
import io.github.mike10004.vhs.testsupport.MultipartFormDataParserTestBase;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class NettyMultipartFormDataParserTest extends MultipartFormDataParserTestBase {

    private static final Charset NONFILE_PARAM_ENCODING_DEFAULT = StandardCharsets.UTF_8;

    @Override
    protected MultipartFormDataParser createParser() {
        return new NettyMultipartFormDataParser();
    }

    @Override
    protected String decodeParamPartData(FormDataPart paramPart) throws IOException {
        if (paramPart.file == null) {
            return "";
        }
        return paramPart.file.asByteSource().asCharSource(NONFILE_PARAM_ENCODING_DEFAULT).read();
    }
}
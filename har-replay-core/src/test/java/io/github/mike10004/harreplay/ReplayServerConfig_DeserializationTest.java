package io.github.mike10004.harreplay;

import com.google.gson.Gson;
import io.github.mike10004.harreplay.ReplayServerConfig.Mapping;
import io.github.mike10004.harreplay.ReplayServerConfig.Replacement;
import io.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransform;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReplayServerConfig_DeserializationTest {

    @Test
    public void empty() {
        ReplayServerConfig original = ReplayServerConfig.empty();
        confirmDeserializationIdentity(original);
    }

    @Test
    public void basic() {
        ReplayServerConfig original = ReplayServerConfig.builder()
                .map(Mapping.literalToPath("foo", "bar"))
                .replace(Replacement.regexToString("(\\s)+", "$1"))
                .replace(Replacement.varToVar("request.method", "request.method"))
                .transformResponse(ResponseHeaderTransform.name(StringLiteral.of("Forwarded-For"), StringLiteral.of("X-Forwarded-For")))
                .build();
        confirmDeserializationIdentity(original);
    }

    @Test
    public void removeHeader() {
        ReplayServerConfig original = ReplayServerConfig.builder()
                .transformResponse(ResponseHeaderTransform.removeByName(StringLiteral.of("X-Forwarded-For")))
                .build();
        confirmDeserializationIdentity(original);
    }

    private void confirmDeserializationIdentity(ReplayServerConfig original) {
        String json = serialist().toJson(original);
        ReplayServerConfig deserialized = serialist().fromJson(json, ReplayServerConfig.class);
        assertEquals(String.format("%s -> %s", original, deserialized), original, deserialized);
    }

    private Gson serialist() {
        return ReplayServerConfig.createSerialist();
    }

}
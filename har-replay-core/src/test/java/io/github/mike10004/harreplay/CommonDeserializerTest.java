package io.github.mike10004.harreplay;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.mike10004.harreplay.ReplayServerConfig.RegexHolder;
import io.github.mike10004.harreplay.ReplayServerConfig.RemoveHeader;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import io.github.mike10004.harreplay.ReplayServerConfig.VariableHolder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CommonDeserializerTest {

    private final Object testCase;

    public CommonDeserializerTest(Object testCase) {
        this.testCase = testCase;
    }

    @Parameters
    public static List<Object> testCases() {
        Random random = new Random(CommonDeserializerTest.class.getName().hashCode());
        Supplier<String> randomStrings = () -> {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String randomString = BaseEncoding.base16().encode(bytes);
            return randomString;
        };
        return ImmutableList.builder()
                .add(StringLiteral.of(randomStrings.get()))
                .add(RegexHolder.of(randomStrings.get()))
                .add(VariableHolder.of(randomStrings.get()))
                .add(RemoveHeader.getInstance())
                .build();
    }

    @Test
    public void exercise() {
        // stock gson can serialize, but we need our custom one to deserialize
        JsonElement element = new Gson().toJsonTree(testCase);
        Gson gson = ReplayServerConfig.createSerialist();
        for (Class<?> interface_ : ReplayServerConfig.INTERFACES_HANDLED_BY_COMMON_DESERIALIZER) {
            if (interface_.isInstance(testCase)) {
                Object deserialized = gson.fromJson(element, interface_);
                assertEquals(String.format("original %s deserializes as %s", testCase, deserialized), testCase, deserialized);
            }
        }
    }
}

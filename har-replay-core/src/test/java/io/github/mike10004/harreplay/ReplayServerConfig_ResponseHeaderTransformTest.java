package io.github.mike10004.harreplay;

import io.github.mike10004.harreplay.ReplayServerConfig.RegexHolder;
import io.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransformNameImage;
import io.github.mike10004.harreplay.ReplayServerConfig.ResponseHeaderTransformNameMatch;
import io.github.mike10004.harreplay.ReplayServerConfig.StringLiteral;
import org.junit.Test;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayServerConfig_ResponseHeaderTransformTest {

    private static StringLiteral sl(String value) {
        return StringLiteral.of(value);
    }

    @Test
    public void StringLiteral_isMatchingHeaderName() {
        StringLiteral t = sl("Foo");
        assertTrue("isMatchingHeaderName", t.isMatchingHeaderName("Foo"));
        assertFalse("isMatchingHeaderName", t.isMatchingHeaderName("Bar"));
    }

    @Test
    public void StringLiteral_transformHeaderName_always() {
        ResponseHeaderTransformNameMatch nameMatch = ResponseHeaderTransformNameMatch.always();
        assertEquals("transformHeaderName", "Foo", sl("Foo").transformHeaderName("Anything", nameMatch.asRegex()));
    }

    @Test
    public void StringLiteral_transformHeaderName_regex() {
        ResponseHeaderTransformNameMatch nameMatch = RegexHolder.of("Hello-(.+)");
        ResponseHeaderTransformNameImage nameImage = sl("Yolo-$1");
        String headerNameBefore = "Hello-Dolly";
        checkState(nameMatch.isMatchingHeaderName(headerNameBefore));
        String actual = nameImage.transformHeaderName(headerNameBefore, nameMatch.asRegex());
        assertEquals("transformHeaderName", "Yolo-Dolly", actual);
    }
}
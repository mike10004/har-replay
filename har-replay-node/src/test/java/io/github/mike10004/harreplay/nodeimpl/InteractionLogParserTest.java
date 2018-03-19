package io.github.mike10004.harreplay.nodeimpl;

import org.junit.Test;
import io.github.mike10004.harreplay.nodeimpl.InteractionLogParser.Interaction;

import static org.junit.Assert.*;

public class InteractionLogParserTest {

    @Test
    public void parseInteraction_ok() {
        String ok = "200 'GET' 'http://www.example.com/' 'text/html' 1270 'string' 'matchedentry'";
        Interaction interaction = new InteractionLogParser().parseInteraction(ok);
        Interaction expected = new Interaction(200, "GET", "http://www.example.com/", "text/html", 1270, "string", "matchedentry");
        assertEquals("parsed", expected, interaction);
    }

    @Test
    public void parseInteraction_notFound() {
        String notFound = "404 'GET' 'http://www.somewhere-else.com/' 'text/plain' 13 'string' 'noentrymatch'";
        Interaction interaction = new InteractionLogParser().parseInteraction(notFound);
        Interaction expected = new Interaction(404, "GET", "http://www.somewhere-else.com/", "text/plain", 13, "string", "noentrymatch");
        assertEquals("parsed", expected, interaction);
    }

    @Test
    public void parseInteraction_contentTypeWithCharset() {
        String ok = "200 'GET' 'http://www.example.com/' 'text/html; charset=UTF-8' 1270 'string' 'matchedentry'";
        Interaction interaction = new InteractionLogParser().parseInteraction(ok);
        Interaction expected = new Interaction(200, "GET", "http://www.example.com/", "text/html; charset=UTF-8", 1270, "string", "matchedentry");
        assertEquals("parsed", expected, interaction);
    }
}
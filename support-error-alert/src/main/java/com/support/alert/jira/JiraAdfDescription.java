package com.support.alert.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Minimal Atlassian Document Format (ADF) for Jira Cloud REST issue descriptions.
 */
public final class JiraAdfDescription {

    private static final int MAX_CHARS = 25_000;

    private JiraAdfDescription() {
    }

    public static ObjectNode fromPlainText(ObjectMapper mapper, String plain) {
        String text = plain == null ? "" : plain;
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS) + "\n… (truncated)";
        }

        ObjectNode textNode = mapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", text);

        ArrayNode paraContent = mapper.createArrayNode();
        paraContent.add(textNode);

        ObjectNode paragraph = mapper.createObjectNode();
        paragraph.put("type", "paragraph");
        paragraph.set("content", paraContent);

        ArrayNode docContent = mapper.createArrayNode();
        docContent.add(paragraph);

        ObjectNode doc = mapper.createObjectNode();
        doc.put("type", "doc");
        doc.put("version", 1);
        doc.set("content", docContent);
        return doc;
    }
}

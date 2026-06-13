package com.izimi.eagent.cortex.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekClientTest {

    @Test
    @DisplayName("parseResponse returns AIResponse from valid JSON")
    void parseResponseValid() {
        String body = """
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"action\\":\\"dig\\",\\"skill\\":\\"mining\\",\\"message\\":\\"mining ore\\"}"
                    }
                  }]
                }
                """;
        AIResponse response = DeepSeekClient.parseResponse(body);
        assertNotNull(response);
        assertEquals("dig", response.action);
        assertEquals("mining", response.skill);
        assertEquals("mining ore", response.message);
    }

    @Test
    @DisplayName("parseResponse strips markdown code block wrapping")
    void parseResponseStripsCodeBlock() {
        String body = """
                {
                  "choices": [{
                    "message": {
                      "content": "```json\\n{\\"action\\":\\"move\\",\\"skill\\":\\"explore\\"}\\n```"
                    }
                  }]
                }
                """;
        AIResponse response = DeepSeekClient.parseResponse(body);
        assertNotNull(response);
        assertEquals("move", response.action);
    }

    @Test
    @DisplayName("parseResponse returns empty sentinel when choices is empty")
    void parseResponseEmptyChoices() {
        String body = """
                {
                  "choices": []
                }
                """;
        AIResponse response = DeepSeekClient.parseResponse(body);
        assertEquals("wait", response.action);
    }

    @Test
    @DisplayName("parseResponse returns empty sentinel when choices is missing")
    void parseResponseMissingChoices() {
        String body = "{}";
        AIResponse response = DeepSeekClient.parseResponse(body);
        assertEquals("wait", response.action);
    }

    @Test
    @DisplayName("parseResponse returns empty sentinel on malformed JSON")
    void parseResponseMalformedJson() {
        AIResponse response = DeepSeekClient.parseResponse("not json");
        assertEquals("wait", response.action);
    }

    @Test
    @DisplayName("parseResponse handles content with params field")
    void parseResponseWithParams() {
        String body = """
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"action\\":\\"craft\\",\\"params\\":{\\"item\\":\\"planks\\",\\"amount\\":4}}"
                    }
                  }]
                }
                """;
        AIResponse response = DeepSeekClient.parseResponse(body);
        assertNotNull(response);
        assertEquals("craft", response.action);
        assertNotNull(response.params);
        assertEquals("planks", response.params.item);
        assertEquals(4, response.params.amount);
    }
}

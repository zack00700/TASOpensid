package fr.alb.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AnthropicRequest {

    public String model;

    @JsonProperty("max_tokens")
    public int maxTokens = 1024;

    public String system;

    public List<AnthropicMessage> messages;

    public static class AnthropicMessage {
        public String role; // "user" or "assistant"
        public String content;

        public AnthropicMessage() {}

        public AnthropicMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}

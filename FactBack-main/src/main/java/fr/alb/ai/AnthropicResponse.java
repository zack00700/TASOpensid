package fr.alb.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AnthropicResponse {

    public String id;

    public List<ContentBlock> content;

    @JsonProperty("stop_reason")
    public String stopReason;

    /**
     * Returns the concatenated text from all text-type content blocks,
     * or an empty string if none are present.
     */
    public String getText() {
        if (content == null || content.isEmpty()) return "";
        return content.stream()
                .filter(c -> "text".equals(c.type))
                .map(c -> c.text)
                .findFirst().orElse("");
    }

    public static class ContentBlock {
        public String type;
        public String text;
    }
}

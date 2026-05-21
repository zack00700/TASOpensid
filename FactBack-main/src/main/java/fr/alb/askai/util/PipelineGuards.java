package fr.alb.askai.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class PipelineGuards {

    private static final Set<String> FORBIDDEN = new HashSet<>(
            Arrays.asList("$where", "$function", "$accumulator", "$out", "$merge"));

    private PipelineGuards() {
    }

    public static void validate(Iterable<JsonNode> pipeline) {
        for (JsonNode stage : pipeline) {
            checkNode(stage);
        }
    }

    private static void checkNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String name = fields.next();
                JsonNode value = node.get(name);
                if (FORBIDDEN.contains(name)) {
                    throw new IllegalArgumentException("Forbidden operator: " + name);
                }
                if ("$limit".equals(name) && value.isNumber() && value.asInt() > 100000) {
                    throw new IllegalArgumentException("$limit too large");
                }
                checkNode(value);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                checkNode(child);
            }
        }
    }
}

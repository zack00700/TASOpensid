package fr.alb.dto.items;

import java.util.List;
import java.util.Map;

public class UpdateItemPatch {
    public String id; // required
    // Flexible key-value for $set operations (e.g., status, weight, etc.)
    public Map<String, Object> fields;
}

package fr.alb.dto.items;

import java.util.List;
import java.util.Map;

public class ItemDiffPayload {
    // For inserts: documents WITHOUT id
    public List<Map<String, Object>> newItems;

    // For updates: each entry has an id + a map of fields to set
    public List<Map<String, Object>> updatedItems;

    // For deletes: just _id strings
    public List<String> removedItemIds;
}

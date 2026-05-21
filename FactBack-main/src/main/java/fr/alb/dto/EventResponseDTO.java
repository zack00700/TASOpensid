package fr.alb.dto;

import fr.alb.yard.model.ItemEvent;
import fr.alb.yard.model.EventConfig;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

public class EventResponseDTO {
    public String id;
    public String itemId;
    public String eventId;
    public Instant timestamp;
    public Instant eventDate; // for compatibility
    public String eventType;
    public String type; // for compatibility
    public String notes;
    public String location;

    public static EventResponseDTO fromEvent(ItemEvent event) {
        EventResponseDTO dto = new EventResponseDTO();
        dto.id = event.getId();
        dto.itemId = event.getItemId();
        dto.eventId = event.getEventId();
        dto.timestamp = event.getEventDate();
        dto.eventDate = event.getEventDate(); // for compatibility

        // Get event type from EventConfig
        EventConfig config = EventConfig.findById(event.getEventId());
        if (config != null) {
            dto.eventType = config.getEventType().toString();
            dto.type = config.getEventType().toString(); // for compatibility
            dto.notes = config.getEventName(); // use event name as notes if available
        }

        return dto;
    }

    public static List<EventResponseDTO> fromEventIds(List<String> eventIds) {
        List<EventResponseDTO> result = new ArrayList<>();
        for (String eventId : eventIds) {
            ItemEvent event = ItemEvent.findById(eventId);
            if (event != null) {
                result.add(fromEvent(event));
            }
        }
        return result;
    }
}
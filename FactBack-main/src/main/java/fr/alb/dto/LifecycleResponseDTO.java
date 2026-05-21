package fr.alb.dto;

import fr.alb.yard.model.Lifecycle;
import fr.alb.type.LifeCycleStatus;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

public class LifecycleResponseDTO {
    public String id;
    public String lifecycleId; // for compatibility
    public String itemId;
    public LifeCycleStatus status;
    public Instant startTime;
    public Instant endTime;
    public List<String> eventIds;
    public List<EventResponseDTO> events; // expanded events

    public static LifecycleResponseDTO fromLifecycle(Lifecycle lc, boolean expandEvents) {
        LifecycleResponseDTO dto = new LifecycleResponseDTO();
        dto.id = lc.getId();
        dto.lifecycleId = lc.getId(); // for compatibility
        dto.itemId = lc.getItemId();
        dto.status = lc.getStatus();
        dto.startTime = lc.getStartTime();
        dto.endTime = lc.getEndTime();
        dto.eventIds = lc.getEventIds();

        if (expandEvents && lc.getEventIds() != null) {
            dto.events = EventResponseDTO.fromEventIds(lc.getEventIds());
        }

        return dto;
    }

    public static List<LifecycleResponseDTO> fromLifecycleIds(List<String> lifecycleIds, boolean expandEvents) {
        List<LifecycleResponseDTO> result = new ArrayList<>();
        for (String lifecycleId : lifecycleIds) {
            Lifecycle lc = Lifecycle.findById(lifecycleId);
            if (lc != null) {
                result.add(fromLifecycle(lc, expandEvents));
            }
        }
        return result;
    }
}
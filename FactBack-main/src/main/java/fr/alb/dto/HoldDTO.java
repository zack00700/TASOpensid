package fr.alb.dto;

import fr.alb.berth.model.Hold;
import fr.alb.type.HoldType;

import java.time.Instant;

/**
 * Public representation of a Hold over the REST API.
 */
public class HoldDTO {
    public String id;
    public String visitId;
    public HoldType type;
    public String reason;
    public Instant openedAt;
    public String openedBy;
    public Instant releasedAt;
    public String releasedBy;
    public String releaseNotes;
    public boolean active;

    public static HoldDTO from(Hold h) {
        if (h == null) return null;
        HoldDTO dto = new HoldDTO();
        dto.id = h.getId();
        dto.visitId = h.visitId;
        dto.type = h.type;
        dto.reason = h.reason;
        dto.openedAt = h.openedAt;
        dto.openedBy = h.openedBy;
        dto.releasedAt = h.releasedAt;
        dto.releasedBy = h.releasedBy;
        dto.releaseNotes = h.releaseNotes;
        dto.active = h.isActive();
        return dto;
    }
}

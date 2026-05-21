package fr.alb.dto;

import fr.alb.yard.model.Item;
import fr.alb.type.ItemStatus;
import fr.alb.type.ItemType;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class ItemResponseDTO {
    public String id;
    public String itemNumber;
    public ItemType itemType;
    public String type;
    public String ownerId;
    public String position;
    public String status;
    public ItemStatus itemStatus;
    public Date lastInspectionDate;
    public Date nextInspectionDate;
    public String notes;
    public String billOfLadingId;
    public String relatedInvoice;
    public Double weight;
    public Double volume;

    // Can be either List<String> (IDs) or List<LifecycleResponseDTO> (expanded)
    public Object lifeCycles;

    public static ItemResponseDTO fromItem(Item item, boolean expandLifecycles) {
        ItemResponseDTO dto = new ItemResponseDTO();
        dto.id = item.getId();
        dto.itemNumber = item.getItemNumber();
        dto.itemType = item.getItemType();
        dto.type = item.getType();
        dto.ownerId = item.getOwnerId();
        dto.position = item.getPosition();
        dto.status = item.getStatus();
        dto.itemStatus = item.getItemStatus();
        dto.lastInspectionDate = item.getLastInspectionDate();
        dto.nextInspectionDate = item.getNextInspectionDate();
        dto.notes = item.getNotes();
        dto.billOfLadingId = item.getBillOfLadingId();
        dto.relatedInvoice = item.getRelatedInvoice();
        dto.weight = item.getWeight();
        dto.volume = item.getVolume();

        if (expandLifecycles && item.getLifeCycles() != null) {
            dto.lifeCycles = LifecycleResponseDTO.fromLifecycleIds(item.getLifeCycles(), true);
        } else {
            dto.lifeCycles = item.getLifeCycles();
        }

        return dto;
    }
}
package fr.alb.dto;

import java.util.Date;

/**
 * DTO representing an Item exposed to API consumers.
 */
public class ItemDto {
    public String id;
    public String itemNumber;
    public String type;
    public String ownerId;
    public String position;
    public String status;
    public Date lastInspectionDate;
    public Date nextInspectionDate;
    public String notes;
    public Double weightKg;
    public Double volumeM3;
    public String billOfLadingId;
}

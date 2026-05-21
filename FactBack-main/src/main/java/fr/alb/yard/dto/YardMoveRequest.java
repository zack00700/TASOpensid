package fr.alb.yard.dto;

import fr.alb.yard.model.ContainerMove;

/** Payload for {@code POST /yard/moves}. */
public class YardMoveRequest {
    public String itemId;
    public String toSlotId;                 // null → exiting the yard
    public ContainerMove.MoveReason reason;
    public String operator;
    public String notes;
}

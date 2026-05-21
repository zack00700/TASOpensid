package fr.alb.dto;

import java.time.Instant;

/**
 * Slim DTO used when searching for vessel visits.
 */
public class VesselVisitSearchDTO {
    public String id;
    public String vesselName;
    public String imo;
    public String voyageIn;
    public String voyageOut;
    public String terminal;
    public Instant eta;
    public Instant etd;
}


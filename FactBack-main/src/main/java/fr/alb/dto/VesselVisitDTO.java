package fr.alb.dto;

import java.time.Instant;

/**
 * Detailed information about a vessel visit, used when populating the
 * transport information snapshot.
 */
public class VesselVisitDTO {
    public String id;
    public String vesselName;
    public String imo;
    public String callSign;
    public String voyageIn;
    public String voyageOut;
    public String operator;
    public String port;
    public String terminal;
    public String berth;
    public Instant eta;
    public Instant etd;
    public Instant ata;
    public Instant atd;
}


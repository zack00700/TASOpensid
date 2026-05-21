package fr.alb.model;

import java.time.Instant;

/**
 * Snapshot of a vessel visit's key fields at the time a Bill of Lading is
 * associated with it. This allows the BoL to remain historically accurate even
 * if the visit later changes.
 */
public class VesselSnapshot {
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


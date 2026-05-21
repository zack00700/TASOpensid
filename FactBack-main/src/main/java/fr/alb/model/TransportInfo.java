package fr.alb.model;

/**
 * Transport information associated with a Bill of Lading. It keeps a reference
 * to the originating transport entity (e.g. vessel visit) and stores a snapshot
 * of the relevant fields for historical integrity.
 */
public class TransportInfo {
    public TransportType type;

    // Common fields
    public String carrier;
    public String modeReference;

    // References
    public String vesselVisitId;
    public String trainServiceId;
    public String truckTripId;

    // Snapshots
    public VesselSnapshot vessel;
    public TrainSnapshot train;
    public TruckSnapshot truck;
}


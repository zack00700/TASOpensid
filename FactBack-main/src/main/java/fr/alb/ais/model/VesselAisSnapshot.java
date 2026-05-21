package fr.alb.ais.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Latest AIS observation per vessel, keyed by MMSI. One document per MMSI.
 * Upserted by AisIngestionService when PositionReport / ShipStaticData arrive.
 * Documents expire automatically via the TTL index on lastSeen (24 h default).
 */
@MongoEntity(collection = "VESSEL_AIS_SNAPSHOT")
public class VesselAisSnapshot extends EntityBase {

    private static final long serialVersionUID = 1L;

    /** Maritime Mobile Service Identity — 9-digit primary key for AIS data. */
    public String mmsi;

    /** From ShipStaticData when present. May lag PositionReport by minutes. */
    public Long imoNumber;
    public String name;
    public String callSign;

    /** From PositionReport. */
    public Double lat;
    public Double lon;
    public Double sog;
    public Double cog;
    public Double trueHeading;
    public Integer navigationalStatus;
    public Instant positionTimestamp;

    /** From ShipStaticData. ETA components are MMDDHHmm UTC, no year (per AIS protocol). */
    public String destination;
    public Integer etaMonth;
    public Integer etaDay;
    public Integer etaHour;
    public Integer etaMinute;

    /** Resolved ETA with year inferred — null until ShipStaticData received. */
    public Instant resolvedEta;

    /** Wallclock when we last received ANY message for this MMSI. TTL is anchored on this. */
    public Instant lastSeen;

    public VesselAisSnapshot() {
        super();
    }
}

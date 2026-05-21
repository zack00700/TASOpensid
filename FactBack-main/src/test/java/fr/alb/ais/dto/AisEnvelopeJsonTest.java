package fr.alb.ais.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AisEnvelopeJsonTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void parsesPositionReportEnvelope() throws Exception {
        String json = """
            {
              "MessageType": "PositionReport",
              "MetaData": { "MMSI": 211281000, "ShipName": "TEST", "latitude": 53.5, "longitude": 9.9, "time_utc": "2026-05-08 07:00:00.000 +0000 UTC" },
              "Message": { "PositionReport": { "UserID": 211281000, "Latitude": 53.5, "Longitude": 9.9, "Sog": 8.4, "Cog": 92.1, "TrueHeading": 90, "NavigationalStatus": 0 } }
            }
            """;

        AisEnvelope env = OM.readValue(json, AisEnvelope.class);
        assertEquals("PositionReport", env.messageType);
        assertEquals(211281000L, env.metadata.mmsi);
        assertNotNull(env.metadata.timestamp());

        PositionReport pr = OM.treeToValue(env.message.get("PositionReport"), PositionReport.class);
        assertEquals(211281000L, pr.userId);
        assertEquals(8.4, pr.sog);
        assertEquals(0, pr.navigationalStatus);
    }

    @Test
    void parsesShipStaticDataEnvelope() throws Exception {
        String json = """
            {
              "MessageType": "ShipStaticData",
              "MetaData": { "MMSI": 211281000, "ShipName": "TEST", "time_utc": "2026-05-08 07:00:00.000 +0000 UTC" },
              "Message": { "ShipStaticData": { "UserID": 211281000, "Name": "TEST", "CallSign": "ABC", "ImoNumber": 9876543, "Destination": "FRLEH", "Eta": { "Month": 6, "Day": 15, "Hour": 14, "Minute": 30 } } }
            }
            """;

        AisEnvelope env = OM.readValue(json, AisEnvelope.class);
        ShipStaticData ssd = OM.treeToValue(env.message.get("ShipStaticData"), ShipStaticData.class);
        assertEquals(211281000L, ssd.userId);
        assertEquals(9876543L, ssd.imoNumber);
        assertEquals("FRLEH", ssd.destination);
        assertEquals(6, ssd.eta.month);
        assertEquals(30, ssd.eta.minute);
    }

    @Test
    void unknownMessageTypeKeepsEnvelopeButLeavesMessageNodeIntact() throws Exception {
        String json = """
            {
              "MessageType": "AidsToNavigationReport",
              "MetaData": { "MMSI": 999, "time_utc": "2026-05-08 07:00:00.000 +0000 UTC" },
              "Message": { "AidsToNavigationReport": { "Foo": "bar" } }
            }
            """;
        AisEnvelope env = OM.readValue(json, AisEnvelope.class);
        assertEquals("AidsToNavigationReport", env.messageType);
        assertNotNull(env.message);
    }
}

package fr.alb.yard.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the merge-update semantics PUT /item/{id} relies on (cahier TC-13): a
 * partial JSON payload must only overwrite the keys it carries and must NEVER
 * erase a previously-stored {@code status}. The resource applies this via
 * {@code mapper.readerForUpdating(existing).readValue(json)}; this test exercises
 * the same Jackson merge against the Item entity, without a DB.
 */
class ItemMergeUpdateTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void hasStoredStatusReflectsExplicitValueOnly() {
        Item item = new Item();
        assertFalse(item.hasStoredStatus(), "never set");
        item.setStatus("   ");
        assertFalse(item.hasStoredStatus(), "blank is not a stored status");
        item.setStatus("IN_YARD");
        assertTrue(item.hasStoredStatus());
    }

    @Test
    void partialPutPreservesStoredStatus() throws Exception {
        Item existing = new Item();
        existing.setStatus("IN_YARD");
        existing.setContainerType("22G1");
        existing.setNotes("original notes");

        // Frontend sends a partial payload that omits "status".
        String partial = "{\"containerType\":\"45G1\",\"notes\":\"updated notes\"}";
        mapper.readerForUpdating(existing).readValue(partial);

        // Present keys are updated...
        assertEquals("45G1", existing.getContainerType());
        assertEquals("updated notes", existing.getNotes());
        // ...and the absent status is preserved (not reset to null/derived).
        assertTrue(existing.hasStoredStatus());
        assertEquals("IN_YARD", existing.getStatus());
    }

    @Test
    void explicitStatusInPayloadStillOverwrites() throws Exception {
        Item existing = new Item();
        existing.setStatus("IN_YARD");

        mapper.readerForUpdating(existing).readValue("{\"status\":\"DEPARTED\"}");

        assertEquals("DEPARTED", existing.getStatus());
    }
}

package fr.alb.parties.service;

import fr.alb.parties.model.ThirdParty;
import fr.alb.service.BaseEntityService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Service for managing ThirdParty entities.
 * Extends BaseEntityService to leverage common update patterns.
 */
@ApplicationScoped
public class ThirdPartyService extends BaseEntityService<ThirdParty> {

    /**
     * Stamps the audit fields (createdAt / updatedAt / version) on a freshly
     * deserialised ThirdParty before it is persisted. Without this the UI
     * renders `new Date(null) = 01/01/1970` for the creation date (cahier TC-03).
     */
    public ThirdParty create(ThirdParty tp) {
        return prepareForCreate(tp);
    }

    /**
     * Updates a third party entity with proper version increment and timestamp.
     *
     * @param updated the updated third party data
     * @param current the current third party from the database
     * @return the prepared third party ready for persistence
     */
    public ThirdParty update(ThirdParty updated, ThirdParty current) {
        return prepareForUpdate(updated, current);
    }
}

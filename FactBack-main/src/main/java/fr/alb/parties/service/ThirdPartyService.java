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

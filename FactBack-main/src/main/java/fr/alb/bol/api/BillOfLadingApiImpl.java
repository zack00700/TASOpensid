package fr.alb.bol.api;

import fr.alb.bol.model.BillOfLading;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BillOfLadingApiImpl implements BillOfLadingApi {

    @Override
    public String findActiveIdByBlNumber(String blNumber) {
        BillOfLading existing = BillOfLading
                .find("blNumber = ?1 and deleted = ?2", blNumber, false)
                .firstResult();
        return existing != null ? existing.getId() : null;
    }
}

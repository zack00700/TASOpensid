package fr.alb.dd;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

import fr.alb.dd.model.DdRule;
import fr.alb.type.DdType;
import fr.alb.type.Status;

@ApplicationScoped
public class DdRuleResolver {

    /**
     * Find the best matching rule for a container.
     * Priority:
     *   1. carrier-specific + containerType match
     *   2. carrier-specific, any containerType (containerTypeCode null or blank)
     *   3. default rule (carrierId null or blank)
     *
     * @param ddType        DEMURRAGE or DETENTION
     * @param carrierId     shipping line code (may be null)
     * @param containerType container type code (may be null)
     * @return Optional<DdRule>
     */
    public Optional<DdRule> resolve(DdType ddType, String carrierId, String containerType) {
        List<DdRule> rules = DdRule.find("ddType = ?1 and status = ?2", ddType, Status.ACTIVE).list();

        // 1. Best match: same carrier AND same containerType
        if (carrierId != null && !carrierId.isBlank()
                && containerType != null && !containerType.isBlank()) {
            Optional<DdRule> best = rules.stream()
                    .filter(r -> carrierId.equals(r.carrierId)
                            && containerType.equals(r.containerTypeCode))
                    .findFirst();
            if (best.isPresent()) {
                return best;
            }
        }

        // 2. Carrier match, any container type (containerTypeCode is null or blank)
        if (carrierId != null && !carrierId.isBlank()) {
            Optional<DdRule> carrierMatch = rules.stream()
                    .filter(r -> carrierId.equals(r.carrierId)
                            && (r.containerTypeCode == null || r.containerTypeCode.isBlank()))
                    .findFirst();
            if (carrierMatch.isPresent()) {
                return carrierMatch;
            }
        }

        // 3. Default rule: carrierId null or blank
        return rules.stream()
                .filter(r -> r.carrierId == null || r.carrierId.isBlank())
                .findFirst();
    }
}

package fr.alb.common;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service de recherche générique pour éliminer la duplication des requêtes
 * de recherche textuelle dans plusieurs Resources.
 */
@ApplicationScoped
public class SearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    /**
     * Interface à implémenter par les entités recherchables
     */
    public interface Searchable {
        /**
         * @return Liste des champs sur lesquels effectuer la recherche
         */
        List<String> getSearchableFields();
    }

    /**
     * Recherche textuelle générique avec regex case-insensitive
     */
    public <T extends PanacheMongoEntityBase> List<T> searchText(
            String query,
            Class<T> entityClass,
            List<String> searchFields,
            Integer limit) {

        if (query == null || query.isBlank()) {
            return entityClass.cast(entityClass).findAll().page(0, limitOrDefault(limit)).list();
        }

        // Échapper les caractères spéciaux regex pour éviter les injections
        String escapedQuery = Pattern.quote(query.trim());
        String regex = "(?i).*" + escapedQuery + ".*";

        // Construire la requête $or pour tous les champs de recherche
        StringBuilder mongoQuery = new StringBuilder("{'$or': [");
        for (int i = 0; i < searchFields.size(); i++) {
            if (i > 0) mongoQuery.append(", ");
            mongoQuery.append("{'").append(searchFields.get(i)).append("': {'$regex': ?1}}");
        }
        mongoQuery.append("]}");

        return entityClass.cast(entityClass)
                .find(mongoQuery.toString(), regex)
                .page(0, limitOrDefault(limit))
                .list();
    }

    /**
     * Recherche avec filtres additionnels
     */
    public <T extends PanacheMongoEntityBase> List<T> searchWithFilters(
            String query,
            Class<T> entityClass,
            List<String> searchFields,
            String additionalFilter,
            Object... params) {

        if (query == null || query.isBlank()) {
            return entityClass.cast(entityClass).find(additionalFilter, params).list();
        }

        String escapedQuery = Pattern.quote(query.trim());
        String regex = "(?i).*" + escapedQuery + ".*";

        StringBuilder mongoQuery = new StringBuilder("{'$and': [");
        mongoQuery.append("{'$or': [");
        for (int i = 0; i < searchFields.size(); i++) {
            if (i > 0) mongoQuery.append(", ");
            mongoQuery.append("{'").append(searchFields.get(i)).append("': {'$regex': ?1}}");
        }
        mongoQuery.append("]}, ").append(additionalFilter).append("]}");

        Object[] allParams = new Object[params.length + 1];
        allParams[0] = regex;
        System.arraycopy(params, 0, allParams, 1, params.length);

        return entityClass.cast(entityClass).find(mongoQuery.toString(), allParams).list();
    }

    private int limitOrDefault(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }
}

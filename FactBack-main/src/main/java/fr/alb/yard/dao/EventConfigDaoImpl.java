package fr.alb.yard.dao;

import java.util.List;

import fr.alb.type.EventScope;
import fr.alb.yard.model.EventConfig;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EventConfigDaoImpl implements EventConfigDao {

	@Override
	@Transactional
	@CacheInvalidateAll(cacheName = "event-cache")
        public void addEventConfig(EventConfig evt) {

                try {
                        evt.persist();
                } catch (Exception e) {
                        Log.error(e);
                        throw new RuntimeException("Error persisting event " + evt.getId(), e);
                }

	}

	@Override
        public List<EventConfig> getEventConfig() {
                List<EventConfig> eventConfigList = EventConfig.listAll();

		Log.info( eventConfigList.size() + " EventConfig found ");

                return eventConfigList;
        }

        @Override
        @CacheResult(cacheName = "event-cache")
        public EventConfig findById(String id) {
                return EventConfig.findById(id);
        }

        @Override
        public List<EventConfig> searchByName(String query) {
                if (query == null || query.isBlank()) {
                        return getEventConfig();
                }
                return EventConfig.find("{'eventName': { $regex: ?1, $options: 'i'}}", query).list();
        }

        @Override
        public List<EventConfig> searchByNameAndScope(String query, EventScope scope) {
                String scopeValue = scope.getValue();
                // Match scope == scopeValue OR BOTH; also treat missing scope as ITEM (legacy)
                String scopeClause;
                if (scope == EventScope.ITEM) {
                        scopeClause = "{ $or: [ {'scope': 'ITEM'}, {'scope': 'BOTH'}, {'scope': { $exists: false }} ] }";
                } else {
                        scopeClause = "{ $or: [ {'scope': '" + scopeValue + "'}, {'scope': 'BOTH'} ] }";
                }
                if (query == null || query.isBlank()) {
                        return EventConfig.find(scopeClause).list();
                }
                String combined = "{ $and: [ " + scopeClause + ", { 'eventName': { $regex: ?1, $options: 'i'}} ] }";
                return EventConfig.find(combined, query).list();
        }


}

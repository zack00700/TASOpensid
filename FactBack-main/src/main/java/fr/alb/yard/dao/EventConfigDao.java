package fr.alb.yard.dao;

import java.util.List;

import fr.alb.type.EventScope;
import fr.alb.yard.model.EventConfig;

public interface EventConfigDao {


        public void addEventConfig(EventConfig e);

        /** Updates an existing event config. Returns true when an entity was found and updated. */
        public boolean updateEventConfig(EventConfig e);

        /** Permanently removes an event config. Returns true when a document was deleted. */
        public boolean deleteEventConfig(String id);

        public List<EventConfig> getEventConfig();

        public EventConfig findById(String id);

        public List<EventConfig> searchByName(String query);

        public List<EventConfig> searchByNameAndScope(String query, EventScope scope);
}

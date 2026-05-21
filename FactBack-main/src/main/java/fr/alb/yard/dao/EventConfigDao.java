package fr.alb.yard.dao;

import java.util.List;

import fr.alb.type.EventScope;
import fr.alb.yard.model.EventConfig;

public interface EventConfigDao {


        public void addEventConfig(EventConfig e);
        public List<EventConfig> getEventConfig();

        public EventConfig findById(String id);

        public List<EventConfig> searchByName(String query);

        public List<EventConfig> searchByNameAndScope(String query, EventScope scope);
}

package fr.alb.model;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Simple key/value setting stored in MongoDB. Used for application wide
 * configuration such as default invoice template.
 */
@MongoEntity(collection = "SETTING")
public class Setting extends EntityBase {

    public String key;
    public String value;
}


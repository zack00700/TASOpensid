package fr.alb.model;

import java.time.Instant;

import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "ASSET")
public class Asset extends EntityBase {

    public String name;
    public String url;
    public long size;
    public String contentType;
    public Instant createdAt;
}

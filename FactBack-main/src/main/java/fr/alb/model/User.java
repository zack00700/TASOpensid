package fr.alb.model;

import java.time.Instant;
import java.util.Set;
import io.quarkus.mongodb.panache.common.MongoEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

@MongoEntity(collection = "USER")
public class User extends EntityBase {

    public String username;

    @JsonIgnore
    public String passwordHash;

    public Set<String> roles;

    public String email;
    public String fullName;
    public boolean active = true;
    public Instant createdAt;
    public Instant lastLoginAt;
    public String lastLoginIp;

    public User() {
        super();
        this.createdAt = Instant.now();
    }

    public User(String username, String email, String fullName, Set<String> roles) {
        this();
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.roles = roles;
    }

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }

    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }

}
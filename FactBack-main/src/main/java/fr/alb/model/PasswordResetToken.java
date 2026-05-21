package fr.alb.model;

import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Entity for storing password reset tokens.
 * Tokens are single-use and expire after a set duration.
 */
@MongoEntity(collection = "PASSWORD_RESET_TOKEN")
public class PasswordResetToken extends EntityBase {

    /**
     * The user ID for whom this reset token was created
     */
    public String userId;

    /**
     * The reset token (should be cryptographically secure)
     */
    public String token;

    /**
     * When this token expires
     */
    public Instant expiresAt;

    /**
     * Whether this token has been used
     */
    public boolean used;

    /**
     * When this token was used (null if not used)
     */
    public Instant usedAt;

    /**
     * Email address associated with this reset request
     */
    public String email;

    /**
     * Check if this token is valid (not expired and not used)
     */
    public boolean isValid() {
        return !used && Instant.now().isBefore(expiresAt);
    }

    /**
     * Find a valid token by token string
     */
    public static PasswordResetToken findByToken(String token) {
        return find("{token: ?1, used: false}", token).firstResult();
    }

    /**
     * Mark this token as used
     */
    public void markAsUsed() {
        this.used = true;
        this.usedAt = Instant.now();
    }
}

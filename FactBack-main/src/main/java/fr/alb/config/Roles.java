package fr.alb.config;

/**
 * Constantes pour les rôles d'autorisation
 */
public final class Roles {
    public static final String USER = "ROLE_USER";
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String INVOICE_ADMIN = "ROLE_INVOICE_ADMIN";
    public static final String TEMPLATES_ADMIN = "ROLE_TEMPLATES_ADMIN";
    public static final String BILLING_USER = "ROLE_BILLING_USER";
    public static final String READONLY = "ROLE_READONLY";

    private Roles() {} // Prevent instantiation
}
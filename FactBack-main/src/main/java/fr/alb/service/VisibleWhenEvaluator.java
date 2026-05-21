package fr.alb.service;

import java.util.Collection;

import org.bson.Document;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Evaluates simple visibility expressions used in templates.
 * The expression is currently limited to a field name which is
 * looked up in the provided data document.
 */
@ApplicationScoped
public class VisibleWhenEvaluator {

    /**
     * Determine if an element should be visible based on the given field.
     * @param field field name to inspect
     * @param data sample data
     * @return {@code true} if the field value is considered truthy
     */
    public boolean isVisible(String field, Document data) {
        if (data == null || field == null) {
            return false;
        }
        Object val = data.get(field);
        return isTruthy(val);
    }

    private boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.doubleValue() != 0d;
        if (val instanceof String s) return !s.isBlank();
        if (val instanceof Collection<?> c) return !c.isEmpty();
        return true;
    }
}

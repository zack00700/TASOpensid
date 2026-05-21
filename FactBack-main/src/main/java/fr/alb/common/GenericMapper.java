package fr.alb.common;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Mapper générique pour éliminer la duplication dans les mappers DTO.
 * Utilise la réflexion pour copier automatiquement les champs similaires.
 */
public final class GenericMapper {

    private GenericMapper() {}

    /**
     * Copie automatiquement les champs du même nom entre source et target
     */
    public static <S, T> T mapBasicFields(S source, T target) {
        if (source == null || target == null) {
            return target;
        }

        Class<?> sourceClass = source.getClass();
        Class<?> targetClass = target.getClass();

        for (Field sourceField : sourceClass.getDeclaredFields()) {
            try {
                Field targetField = targetClass.getDeclaredField(sourceField.getName());

                if (isCompatibleType(sourceField.getType(), targetField.getType())) {
                    sourceField.setAccessible(true);
                    targetField.setAccessible(true);

                    Object value = sourceField.get(source);
                    if (value != null) {
                        targetField.set(target, convertValue(value, targetField.getType()));
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // Field doesn't exist in target or not accessible - skip
            }
        }

        return target;
    }

    /**
     * Création et mapping en une seule opération
     */
    public static <S, T> T mapToNew(S source, Class<T> targetClass) {
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            return mapBasicFields(source, target);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate " + targetClass.getSimpleName(), e);
        }
    }

    private static boolean isCompatibleType(Class<?> sourceType, Class<?> targetType) {
        return sourceType.equals(targetType) ||
                (sourceType.isPrimitive() && isWrapperType(targetType, sourceType)) ||
                (targetType.isPrimitive() && isWrapperType(sourceType, targetType)) ||
                isDateTimeCompatible(sourceType, targetType);
    }

    private static boolean isWrapperType(Class<?> wrapper, Class<?> primitive) {
        return (primitive == int.class && wrapper == Integer.class) ||
                (primitive == long.class && wrapper == Long.class) ||
                (primitive == double.class && wrapper == Double.class) ||
                (primitive == boolean.class && wrapper == Boolean.class) ||
                (primitive == float.class && wrapper == Float.class);
    }

    private static boolean isDateTimeCompatible(Class<?> type1, Class<?> type2) {
        return (type1 == Date.class || type1 == Instant.class ||
                type1 == LocalDate.class || type1 == LocalDateTime.class) &&
                (type2 == Date.class || type2 == Instant.class ||
                        type2 == LocalDate.class || type2 == LocalDateTime.class);
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value.getClass().equals(targetType)) {
            return value;
        }

        // Conversions de dates
        if (value instanceof Date date) {
            if (targetType == Instant.class) return date.toInstant();
            if (targetType == LocalDateTime.class) return date.toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }

        if (value instanceof Instant instant) {
            if (targetType == Date.class) return Date.from(instant);
            if (targetType == LocalDateTime.class) return instant
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }

        return value;
    }
}

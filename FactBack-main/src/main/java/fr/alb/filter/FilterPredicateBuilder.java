package fr.alb.filter;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;

import fr.alb.billing.model.CalcFilter;
import fr.alb.type.FilterOp;
import fr.alb.type.FilterTarget;
import fr.alb.type.ValueType;

/**
 * Utility to translate {@link CalcFilter} definitions into MongoDB {@link Bson} predicates.
 */
public class FilterPredicateBuilder {

    public static Bson andAll(List<CalcFilter> filters) {
        return Filters.and(filters.stream().map(FilterPredicateBuilder::toPredicate).toList());
    }

    public static Bson toPredicate(CalcFilter f) {
        String fieldName = f.field;
        if (f.target == FilterTarget.BILL_OF_LADING) {
            fieldName = "bol." + fieldName;
        }

        Object value = parseValue(f.valueType, f.value);
        Bson base;
        if (f.op == FilterOp.BETWEEN) {
            Object valueTo = parseValue(f.valueType, f.valueTo);
            base = Filters.and(Filters.gte(fieldName, value), Filters.lte(fieldName, valueTo));
        } else {
            base = switch (f.op) {
                case EQ -> Filters.eq(fieldName, value);
                case LT -> Filters.lt(fieldName, value);
                case GT -> Filters.gt(fieldName, value);
                default -> throw new IllegalArgumentException("Unsupported op: " + f.op);
            };
        }

        if (f.target == FilterTarget.BILL_OF_LADING) {
            if (Boolean.TRUE.equals(f.includeNull)) {
                return Filters.or(base, Filters.eq("bol", null));
            } else {
                return Filters.and(Filters.ne("bol", null), base);
            }
        }
        return base;
    }

    private static Object parseValue(ValueType type, String v) {
        return switch (type) {
            case STRING -> v;
            case INT -> Integer.valueOf(v);
            case DATE -> Date.from(Instant.parse(v));
        };
    }
}

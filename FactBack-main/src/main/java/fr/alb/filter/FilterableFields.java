package fr.alb.filter;

import java.util.List;
import java.util.Map;

import fr.alb.type.FilterTarget;
import fr.alb.type.ValueType;

/**
 * Static catalog describing filterable fields and their value types.
 */
public class FilterableFields {

    public static class Field {
        public String field;
        public ValueType type;
        public Field(String field, ValueType type) {
            this.field = field;
            this.type = type;
        }
    }

    public static final Map<FilterTarget, List<Field>> CATALOG = Map.of(
        FilterTarget.ITEM, List.of(
            new Field("itemNumber", ValueType.STRING),
            new Field("itemType", ValueType.STRING),
            new Field("size", ValueType.INT),
            new Field("lastInspectionDate", ValueType.DATE)
        ),
        FilterTarget.BILL_OF_LADING, List.of(
            new Field("bolNumber", ValueType.STRING),
            new Field("eta", ValueType.DATE),
            new Field("customerRef", ValueType.STRING),
            new Field("packageCount", ValueType.INT)
        )
    );

    public static ValueType getType(FilterTarget target, String field) {
        return CATALOG.getOrDefault(target, List.of()).stream()
            .filter(f -> f.field.equals(field))
            .map(f -> f.type)
            .findFirst()
            .orElse(null);
    }
}

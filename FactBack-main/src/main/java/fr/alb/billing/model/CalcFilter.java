package fr.alb.billing.model;

import fr.alb.type.FilterOp;
import fr.alb.type.FilterTarget;
import fr.alb.type.ValueType;

/**
 * Clause describing a filter applied when selecting items/bill of lading.
 */
public class CalcFilter {
    public FilterTarget target;      // ITEM | BILL_OF_LADING
    public String field;             // e.g., "itemNumber", "size", etc.
    public ValueType valueType;      // STRING | INT | DATE
    public FilterOp op;              // EQ | LT | GT | BETWEEN
    public String value;             // for EQ/LT/GT
    public String valueTo;           // upper bound for BETWEEN
    public Boolean includeNull;      // only for BOL

    public CalcFilter() {}
}

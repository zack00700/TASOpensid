package fr.alb.billing.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentTermsParserTest {

    @Test
    void parsesNet30() {
        assertEquals(30, PaymentTermsParser.parseDays("NET30"));
    }

    @Test
    void parsesNet60() {
        assertEquals(60, PaymentTermsParser.parseDays("NET60"));
    }

    @Test
    void parsesNet90() {
        assertEquals(90, PaymentTermsParser.parseDays("NET90"));
    }

    @Test
    void caseInsensitive() {
        assertEquals(30, PaymentTermsParser.parseDays("net30"));
        assertEquals(45, PaymentTermsParser.parseDays("Net45"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(30, PaymentTermsParser.parseDays("  NET30  "));
    }

    @Test
    void fallsBackTo30OnNull() {
        assertEquals(30, PaymentTermsParser.parseDays(null));
    }

    @Test
    void fallsBackTo30OnEmpty() {
        assertEquals(30, PaymentTermsParser.parseDays(""));
        assertEquals(30, PaymentTermsParser.parseDays("   "));
    }

    @Test
    void fallsBackTo30OnUnknownFormat() {
        assertEquals(30, PaymentTermsParser.parseDays("FOO"));
        assertEquals(30, PaymentTermsParser.parseDays("30 days net"));
        assertEquals(30, PaymentTermsParser.parseDays("COD"));
    }

    @Test
    void fallsBackTo30OnMalformedNet() {
        assertEquals(30, PaymentTermsParser.parseDays("NET"));
        assertEquals(30, PaymentTermsParser.parseDays("NETabc"));
        assertEquals(30, PaymentTermsParser.parseDays("NET-1"));
    }
}

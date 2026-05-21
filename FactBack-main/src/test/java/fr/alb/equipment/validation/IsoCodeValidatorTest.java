package fr.alb.equipment.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsoCodeValidatorTest {

    @Test
    void validIsoCodeFourCharsUppercaseAndDigits() {
        assertTrue(IsoCodeValidator.isValidCode("22G1"));
        assertTrue(IsoCodeValidator.isValidCode("L5G1"));
        assertTrue(IsoCodeValidator.isValidCode("0000"));
        assertTrue(IsoCodeValidator.isValidCode("ZZZZ"));
    }

    @Test
    void rejectsLowercase() {
        assertFalse(IsoCodeValidator.isValidCode("22g1"));
        assertFalse(IsoCodeValidator.isValidCode("l5g1"));
    }

    @Test
    void rejectsWrongLength() {
        assertFalse(IsoCodeValidator.isValidCode("22G"));
        assertFalse(IsoCodeValidator.isValidCode("22G11"));
        assertFalse(IsoCodeValidator.isValidCode(""));
    }

    @Test
    void rejectsNullAndSpecialCharacters() {
        assertFalse(IsoCodeValidator.isValidCode(null));
        assertFalse(IsoCodeValidator.isValidCode("22-G"));
        assertFalse(IsoCodeValidator.isValidCode("22.G1"));
    }
}

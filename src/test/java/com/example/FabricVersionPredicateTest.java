package com.example;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FabricVersionPredicateTest {

    @Test
    @DisplayName("Verify VersionPredicate matches 1.21.x and 26.2 constraints")
    void testVersionPredicates() throws VersionParsingException {
        Version v26_2 = Version.parse("26.2");
        Version v1_21 = Version.parse("1.21");
        Version v1_21_1 = Version.parse("1.21.1");
        Version v1_21_2 = Version.parse("1.21.2");
        Version v1_21_4 = Version.parse("1.21.4");
        Version v1_20 = Version.parse("1.20.4");

        VersionPredicate predExact26_2 = VersionPredicate.parse("26.2");
        assertTrue(predExact26_2.test(v26_2), "26.2 predicate matches 26.2 version");
        assertFalse(predExact26_2.test(v1_21), "26.2 predicate does not match 1.21");

        VersionPredicate predTilde1_21 = VersionPredicate.parse("~1.21");
        assertTrue(predTilde1_21.test(v1_21), "~1.21 matches 1.21");
        assertTrue(predTilde1_21.test(v1_21_1), "~1.21 matches 1.21.1");
        assertTrue(predTilde1_21.test(v1_21_2), "~1.21 matches 1.21.2");
        assertTrue(predTilde1_21.test(v1_21_4), "~1.21 matches 1.21.4");
        assertFalse(predTilde1_21.test(v1_20), "~1.21 does not match 1.20.4");
        assertFalse(predTilde1_21.test(v26_2), "~1.21 does not match 26.2");

        VersionPredicate predGte26 = VersionPredicate.parse(">=26");
        assertTrue(predGte26.test(v26_2), ">=26 matches 26.2");
        assertFalse(predGte26.test(v1_21), ">=26 does not match 1.21");
    }
}

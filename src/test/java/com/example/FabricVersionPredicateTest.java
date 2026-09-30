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
    @DisplayName("Verify VersionPredicate matches 1.21.x constraints")
    void testVersionPredicates() throws VersionParsingException {
        Version v1_21 = Version.parse("1.21");
        Version v1_21_1 = Version.parse("1.21.1");
        Version v1_21_2 = Version.parse("1.21.2");
        Version v1_21_4 = Version.parse("1.21.4");
        Version v1_20 = Version.parse("1.20.4");
        Version v26_1 = Version.parse("26.1");
        Version v26_2 = Version.parse("26.2");

        VersionPredicate predTilde1_21 = VersionPredicate.parse("~1.21");
        assertTrue(predTilde1_21.test(v1_21), "~1.21 matches 1.21");
        assertTrue(predTilde1_21.test(v1_21_1), "~1.21 matches 1.21.1");
        assertTrue(predTilde1_21.test(v1_21_2), "~1.21 matches 1.21.2");
        assertTrue(predTilde1_21.test(v1_21_4), "~1.21 matches 1.21.4");
        assertFalse(predTilde1_21.test(v1_20), "~1.21 does not match 1.20.4");
        assertFalse(predTilde1_21.test(v26_1), "~1.21 does not match 26.1");
        assertFalse(predTilde1_21.test(v26_2), "~1.21 does not match 26.2");
    }
}

package com.vocablend.vocablend_be.Service.DeviceWord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewOutcomeTest {

    @Test
    void fromParam_parsesWireValues() {
        assertEquals(ReviewOutcome.GOT_IT, ReviewOutcome.fromParam("gotIt"));
        assertEquals(ReviewOutcome.STILL_LEARNING, ReviewOutcome.fromParam("stillLearning"));
    }

    @Test
    void fromParam_rejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> ReviewOutcome.fromParam("maybe"));
    }

    @Test
    void fromParam_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> ReviewOutcome.fromParam(null));
    }
}

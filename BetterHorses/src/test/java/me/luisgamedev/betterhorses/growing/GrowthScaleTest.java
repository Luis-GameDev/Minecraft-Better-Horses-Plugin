package me.luisgamedev.betterhorses.growing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthScaleTest {
    @Test void scaleIsContinuousAndBounded() {
        double previous = 0;
        for (int stage = 1; stage <= 10; stage++) {
            double scale = GrowthScale.forStage(stage, 1.3);
            assertTrue(scale > previous);
            previous = scale;
        }
        assertEquals(1.3, GrowthScale.forStage(10, 1.3), 1e-9);
        assertEquals(GrowthScale.forStage(1, 1.3), GrowthScale.forStage(-4, 1.3));
    }
}

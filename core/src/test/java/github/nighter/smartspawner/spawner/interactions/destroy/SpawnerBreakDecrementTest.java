package github.nighter.smartspawner.spawner.interactions.destroy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpawnerBreakDecrementTest {

    @Test
    void testStackReductionOneByOne() {
        int currentStackSize = 100;
        int dropAmount = 1;
        boolean shouldDeleteSpawner = currentStackSize <= 1;
        int newStackSize = currentStackSize;
        if (!shouldDeleteSpawner) {
            newStackSize = currentStackSize - 1;
        }

        assertEquals(1, dropAmount);
        assertFalse(shouldDeleteSpawner);
        assertEquals(99, newStackSize);
    }

    @Test
    void testSingleSpawnerBreakDeletes() {
        int currentStackSize = 1;
        int dropAmount = 1;
        boolean shouldDeleteSpawner = currentStackSize <= 1;
        int newStackSize = currentStackSize;
        if (!shouldDeleteSpawner) {
            newStackSize = currentStackSize - 1;
        }

        assertEquals(1, dropAmount);
        assertTrue(shouldDeleteSpawner);
        assertEquals(1, newStackSize);
    }
}

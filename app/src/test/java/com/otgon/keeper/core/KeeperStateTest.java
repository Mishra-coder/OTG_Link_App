package com.otgon.keeper.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeeperStateTest {

    private static final long BOOT = 1_000_000L;
    private static final long INSTALL = 1_100_000L;
    private static final long STARTED = 1_200_000L;

    @Test
    public void serviceThatVanishedWithoutStopping_isAKill() {
        assertTrue(KeeperState.isUnexpectedDeath(true, STARTED, BOOT, INSTALL));
    }

    @Test
    public void cleanlyStoppedService_isNotAKill() {
        assertFalse(KeeperState.isUnexpectedDeath(false, STARTED, BOOT, INSTALL));
    }

    @Test
    public void serviceFromBeforeReboot_isNotAKill() {
        long rebootedLater = STARTED + 1;
        assertFalse(KeeperState.isUnexpectedDeath(true, STARTED, rebootedLater, INSTALL));
    }

    @Test
    public void serviceFromBeforeAppUpdate_isNotAKill() {
        long updatedLater = STARTED + 1;
        assertFalse(KeeperState.isUnexpectedDeath(true, STARTED, BOOT, updatedLater));
    }
}

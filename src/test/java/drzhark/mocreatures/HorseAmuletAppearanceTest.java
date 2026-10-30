package drzhark.mocreatures;

import org.junit.Test;

import drzhark.mocreatures.entity.animal.MoCEntityHorse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HorseAmuletAppearanceTest {
    @Test
    public void releaseReversesCaptureFadeWithoutStartingCapture()
    {
        MoCEntityHorse horse = mock(MoCEntityHorse.class, CALLS_REAL_METHODS);
        horse.ticksExisted = 250;
        horse.startAmuletAppearance();
        for (int tick = 0; tick <= 100; tick++)
        {
            horse.ticksExisted = 250 + tick;
            assertEquals(100 - tick, horse.getAmuletFadeCounter());
            assertEquals(0, horse.getVanishC());
            assertFalse(horse.isDead);
        }
        horse.ticksExisted += 1000;
        assertEquals(0, horse.getAmuletFadeCounter());
    }

    @Test
    public void spawnDataCarriesRemainingFadeForNewAndLateObservers()
    {
        MoCEntityHorse serverHorse = mock(MoCEntityHorse.class, CALLS_REAL_METHODS);
        serverHorse.startAmuletAppearance();
        for (int elapsed : new int[] { 0, 40, 100, 150 })
        {
            serverHorse.ticksExisted = elapsed;
            MoCEntityHorse clientHorse = mock(MoCEntityHorse.class, CALLS_REAL_METHODS);
            clientHorse.ticksExisted = 3;
            ByteBuf data = Unpooled.buffer();
            try
            {
                serverHorse.writeSpawnData(data);
                clientHorse.readSpawnData(data);
                assertEquals(Math.max(0, 100 - elapsed), clientHorse.getAmuletFadeCounter());
                clientHorse.ticksExisted += 100;
                assertEquals(0, clientHorse.getAmuletFadeCounter());
            }
            finally
            {
                data.release();
            }
        }
    }

    @Test
    public void ordinaryHorseDoesNotFadeAndCaptureStillTakesPrecedence()
    {
        MoCEntityHorse horse = mock(MoCEntityHorse.class, CALLS_REAL_METHODS);
        assertEquals(0, horse.getAmuletFadeCounter());
        horse.startAmuletAppearance();
        horse.setVanishC((byte) 20);
        assertEquals(20, horse.getAmuletFadeCounter());
        horse.setVanishC((byte) 80);
        assertEquals(80, horse.getAmuletFadeCounter());
    }
}

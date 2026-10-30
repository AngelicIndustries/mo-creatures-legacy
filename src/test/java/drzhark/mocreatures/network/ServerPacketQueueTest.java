package drzhark.mocreatures.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cpw.mods.fml.common.gameevent.TickEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ServerPacketQueueTest {
    private final MoCServerPacketQueue queue = new MoCServerPacketQueue();

    @Before
    public void start() { MoCServerPacketQueue.start(); }

    @After
    public void stop() { MoCServerPacketQueue.stop(); }

    @Test
    public void runsOnlyOnStartTickAndOnTheTickThread() throws Exception
    {
        AtomicReference<Thread> executedOn = new AtomicReference<Thread>();
        Thread producer = new Thread(() -> MoCServerPacketQueue.enqueue(() -> executedOn.set(Thread.currentThread())));
        producer.start();
        producer.join();
        assertNull(executedOn.get());
        queue.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        assertNull(executedOn.get());
        tick();
        assertSame(Thread.currentThread(), executedOn.get());
    }

    @Test
    public void preservesOrderAndContinuesAfterAnInvalidAction()
    {
        List<Integer> order = new ArrayList<Integer>();
        MoCServerPacketQueue.enqueue(() -> order.add(1));
        MoCServerPacketQueue.enqueue(() -> { throw new IllegalArgumentException("test packet"); });
        MoCServerPacketQueue.enqueue(() -> order.add(2));
        tick();
        assertEquals(Arrays.asList(1, 2), order);
    }

    @Test
    public void doesNotCarryWorkAcrossServerRestarts()
    {
        AtomicInteger count = new AtomicInteger();
        MoCServerPacketQueue.enqueue(count::incrementAndGet);
        MoCServerPacketQueue.stop();
        MoCServerPacketQueue.enqueue(count::incrementAndGet);
        MoCServerPacketQueue.start();
        tick();
        assertEquals(0, count.get());
        MoCServerPacketQueue.enqueue(count::incrementAndGet);
        tick();
        assertEquals(1, count.get());
    }

    @Test
    public void boundsBacklogAndWorkPerTick()
    {
        AtomicInteger count = new AtomicInteger();
        for (int i = 0; i < 10000; i++) MoCServerPacketQueue.enqueue(count::incrementAndGet);
        tick();
        int firstTick = count.get();
        assertTrue(firstTick > 0 && firstTick < 10000);
        for (int i = 0; i < 100; i++) tick();
        assertTrue(count.get() > firstTick && count.get() < 10000);
    }

    private void tick() { queue.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START)); }
}

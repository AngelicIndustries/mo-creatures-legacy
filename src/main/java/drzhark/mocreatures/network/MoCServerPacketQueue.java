package drzhark.mocreatures.network;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import drzhark.mocreatures.utils.MoCLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

/** Adapted from KAMKEEL/mocreaturesdev commit 2d40166eeb892a60d71f8409a9324aa6710dea45. */
public final class MoCServerPacketQueue {
    private static final int MAX_PENDING = 1024;
    private static final int MAX_PER_TICK = 256;
    private static final Queue<Runnable> TASKS = new ArrayDeque<Runnable>();
    private static boolean accepting;

    public static void enqueue(NetHandlerPlayServer handler, Consumer<EntityPlayerMP> action)
    {
        final EntityPlayerMP player = handler.playerEntity;
        enqueue(() -> {
            // Respawns replace playerEntity; disconnects may leave packets waiting for a tick.
            if (player != null && handler.playerEntity == player && handler.netManager.isChannelOpen()
                    && !player.isDead && player.worldObj != null && !player.worldObj.isRemote)
            {
                action.accept(player);
            }
        });
    }

    static synchronized void enqueue(Runnable task)
    {
        if (accepting && TASKS.size() < MAX_PENDING)
        {
            TASKS.add(task);
        }
    }

    public static synchronized void start()
    {
        TASKS.clear();
        accepting = true;
    }

    public static synchronized void stop()
    {
        accepting = false;
        TASKS.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START)
        {
            return;
        }
        final int count;
        synchronized (MoCServerPacketQueue.class)
        {
            count = Math.min(TASKS.size(), MAX_PER_TICK);
        }
        for (int i = 0; i < count; i++)
        {
            Runnable task;
            synchronized (MoCServerPacketQueue.class)
            {
                task = TASKS.poll();
            }
            if (task == null)
            {
                break;
            }
            try
            {
                task.run();
            }
            catch (RuntimeException exception)
            {
                MoCLog.logger.error("Error while handling a server packet", exception);
            }
        }
    }
}

package drzhark.mocreatures;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import drzhark.mocreatures.entity.IMoCEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class MoCPlayerTracker
{
    @SubscribeEvent
    public void onPlayerLogout(PlayerLoggedOutEvent event) {
        EntityPlayer player = event.player;
        Entity mount = player.ridingEntity;
        if (!player.worldObj.isRemote && mount instanceof IMoCEntity && !mount.isDead
                && mount.worldObj == player.worldObj && mount.riddenByEntity == player)
        {
            ((IMoCEntity)mount).riderIsDisconnecting(true);
        }
    }
}

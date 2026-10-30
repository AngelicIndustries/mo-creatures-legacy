package drzhark.mocreatures.network.message;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import drzhark.mocreatures.entity.IMoCEntity;
import drzhark.mocreatures.network.MoCServerPacketQueue;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;

public class MoCMessageEntityDive implements IMessage, IMessageHandler<MoCMessageEntityDive, IMessage> {

    public MoCMessageEntityDive() {}

    @Override
    public void toBytes(ByteBuf buffer)
    {
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
    }

    @Override
    public IMessage onMessage(MoCMessageEntityDive message, MessageContext context)
    {
        MoCServerPacketQueue.enqueue(context.getServerHandler(), player -> {
            Entity mount = player.ridingEntity;
            if (mount instanceof IMoCEntity && !mount.isDead && mount.riddenByEntity == player)
            {
                ((IMoCEntity) mount).makeEntityDive();
            }
        });
        return null;
    }
}
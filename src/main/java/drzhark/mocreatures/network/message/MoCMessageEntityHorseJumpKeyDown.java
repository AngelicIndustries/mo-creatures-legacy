package drzhark.mocreatures.network.message;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import drzhark.mocreatures.entity.animal.MoCEntityHorse;
import drzhark.mocreatures.network.MoCServerPacketQueue;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;

public class MoCMessageEntityHorseJumpKeyDown implements IMessage, IMessageHandler<MoCMessageEntityHorseJumpKeyDown, IMessage> {
 
	public MoCMessageEntityHorseJumpKeyDown()
    {
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
    }

    @Override
    public IMessage onMessage(MoCMessageEntityHorseJumpKeyDown message, MessageContext context)
    {
        MoCServerPacketQueue.enqueue(context.getServerHandler(), player -> {
            Entity mount = player.ridingEntity;
            if (mount instanceof MoCEntityHorse && !mount.isDead && mount.riddenByEntity == player)
            {
                ((MoCEntityHorse) mount).setJumpKeyDown(true);
            }
        });
        return null;
    }

    @Override
    public String toString()
    {
        return String.format("MoCMessageEntityHorseJumpKeyDown");
    }
}
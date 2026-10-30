package drzhark.mocreatures.network.message;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import drzhark.mocreatures.MoCPetData;
import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.network.MoCServerPacketQueue;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class MoCMessageUpdatePetName implements IMessage, IMessageHandler<MoCMessageUpdatePetName, IMessage> {
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_NAME_BYTES = MAX_NAME_LENGTH * 4;

    String name;
    int entityId;

    public MoCMessageUpdatePetName() {}

    public MoCMessageUpdatePetName(int entityId)
    {
        this.entityId = entityId;
    }

    public MoCMessageUpdatePetName(int entityId, String name)
    {
        this.entityId = entityId;
        this.name = name;
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        byte[] bytes = limitName(name).getBytes(StandardCharsets.UTF_8);
        // Preserve Legacy's int-length, UTF-8 bytes, entity-id wire format.
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
        buffer.writeInt(entityId);
    }

    public static int findByteLengthOfString(String string)
    {
        return string.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        if (buffer.readableBytes() < 8)
        {
            throw new DecoderException("Truncated pet name packet");
        }
        int nameLength = buffer.readInt();
        if (nameLength < 0 || nameLength > MAX_NAME_BYTES || nameLength > buffer.readableBytes() - 4)
        {
            throw new DecoderException("Invalid pet name byte length");
        }
        try
        {
            name = StandardCharsets.UTF_8.newDecoder().decode(buffer.nioBuffer(buffer.readerIndex(), nameLength)).toString();
        }
        catch (CharacterCodingException exception)
        {
            throw new DecoderException("Invalid UTF-8 pet name", exception);
        }
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH)
        {
            throw new DecoderException("Pet name exceeds 32 characters");
        }
        buffer.skipBytes(nameLength);
        entityId = buffer.readInt();
    }

    @Override
    public IMessage onMessage(MoCMessageUpdatePetName message, MessageContext context)
    {
        final int requestedEntityId = message.entityId;
        final String requestedName = limitName(message.name);
        MoCServerPacketQueue.enqueue(context.getServerHandler(), player -> renamePet(player, requestedEntityId, requestedName));
        return null;
    }

    private static String limitName(String name)
    {
        if (name == null)
        {
            return "";
        }
        return name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH
                ? name : name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }

    // Ownership and persistence handling adapted from KAMKEEL commit 2d40166eeb892a60d71f8409a9324aa6710dea45.
    private static void renamePet(EntityPlayerMP player, int entityId, String name)
    {
        Entity entity = player.worldObj.getEntityByID(entityId);
        if (!(entity instanceof IMoCTameable) || entity.isDead)
        {
            return;
        }
        IMoCTameable pet = (IMoCTameable) entity;
        String ownerName = pet.getOwnerName();
        if (!pet.getIsTamed() || ownerName == null || !ownerName.equals(player.getCommandSenderName()))
        {
            return;
        }
        pet.setName(name);
        if (MoCreatures.instance == null || MoCreatures.instance.mapData == null || pet.getOwnerPetId() == -1)
        {
            return;
        }
        MoCPetData petData = MoCreatures.instance.mapData.getPetData(ownerName);
        if (petData == null)
        {
            return;
        }
        NBTTagList pets = petData.getOwnerRootNBT().getTagList("TamedList", 10);
        for (int i = 0; i < pets.tagCount(); i++)
        {
            NBTTagCompound petTag = pets.getCompoundTagAt(i);
            if (petTag.getInteger("PetId") == pet.getOwnerPetId())
            {
                petTag.setString("Name", name);
                MoCreatures.instance.mapData.markDirty();
                return;
            }
        }
    }

    @Override
    public String toString()
    {
        return String.format("MoCMessageUpdatePetName - entityId:%s, name:%s", entityId, name);
    }
}

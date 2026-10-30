package drzhark.mocreatures.command;

import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.utils.MoCLog;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

final class MoCPetTeleport {
    enum Result { SUCCESS, NOT_FOUND, FAILED }

    private MoCPetTeleport() {}

    static void teleportPet(WorldServer world, EntityPlayerMP player, NBTTagCompound nbt, ICommandSender sender)
    {
        int petId = nbt.getInteger("PetId");
        if (world == null)
        {
            sender.addChatMessage(new ChatComponentTranslation("The pet's dimension is not loaded. Visit it before trying again."));
            return;
        }
        Result result = teleportLoadedPet(world, player, petId, sender);
        if (result == Result.NOT_FOUND)
        {
            double posX = nbt.getTagList("Pos", 6).func_150309_d(0);
            double posY = nbt.getTagList("Pos", 6).func_150309_d(1);
            double posZ = nbt.getTagList("Pos", 6).func_150309_d(2);
            int x = MathHelper.floor_double( posX );
            int y = MathHelper.floor_double( posY );
            int z = MathHelper.floor_double( posZ );
            sender.addChatMessage(new ChatComponentTranslation("Found unloaded pet " + EnumChatFormatting.GREEN + nbt.getString("id") + EnumChatFormatting.WHITE + " with name " + EnumChatFormatting.AQUA + nbt.getString("Name") + EnumChatFormatting.WHITE + " at location " + EnumChatFormatting.LIGHT_PURPLE + x + EnumChatFormatting.WHITE + ", " + EnumChatFormatting.LIGHT_PURPLE + y + EnumChatFormatting.WHITE + ", " + EnumChatFormatting.LIGHT_PURPLE + z + EnumChatFormatting.WHITE + " with Pet ID " + EnumChatFormatting.BLUE + nbt.getInteger("PetId")));
            world.getChunkFromChunkCoords(x >> 4, z >> 4);
            result = teleportLoadedPet(world, player, petId, sender);
        }
        if (result != Result.SUCCESS)
        {
            sender.addChatMessage(new ChatComponentTranslation("Unable to transfer pet ID " + petId + ". The pet has not been moved."));
        }
    }

    static Result teleportLoadedPet(WorldServer world, EntityPlayerMP player, int petId, ICommandSender sender)
    {
        if (world == null || world.isRemote || player == null || !(player.worldObj instanceof WorldServer)
                || player.worldObj.isRemote || petId < 0)
        {
            return Result.FAILED;
        }
        WorldServer destination = (WorldServer)player.worldObj;
        for (int i = 0; i < world.loadedEntityList.size(); i++)
        {
            Entity entity = (Entity)world.loadedEntityList.get(i);
            if (!(entity instanceof IMoCTameable) || entity.isDead || entity.worldObj != world) continue;
            IMoCTameable pet = (IMoCTameable)entity;
            if (!pet.getIsTamed() || pet.getOwnerPetId() != petId || pet.getName() == null || pet.getName().isEmpty()
                    || pet.getOwnerName() == null || !pet.getOwnerName().equalsIgnoreCase(player.getCommandSenderName())) continue;

            if (world == destination)
            {
                entity.setPosition(player.posX, player.posY, player.posZ);
            }
            else if (!transfer(entity, world, destination, player))
            {
                return Result.FAILED;
            }
            sender.addChatMessage(new ChatComponentTranslation(EnumChatFormatting.GREEN + pet.getName()
                    + EnumChatFormatting.WHITE + " has been tp'd to location " + Math.round(player.posX) + ", "
                    + Math.round(player.posY) + ", " + Math.round(player.posZ) + " in dimension " + destination.provider.dimensionId));
            return Result.SUCCESS;
        }
        return Result.NOT_FOUND;
    }

    private static boolean transfer(Entity entity, WorldServer source, WorldServer destination, EntityPlayerMP player)
    {
        Entity replacement = null;
        try
        {
            replacement = EntityList.createEntityByName(EntityList.getEntityString(entity), destination);
            if (replacement == null) return false;
            replacement.copyDataFrom(entity, true);
            // Entity NBT includes the old dimension, even when constructed in the destination world.
            replacement.dimension = destination.provider.dimensionId;
            replacement.setPosition(player.posX, player.posY, player.posZ);
            if (replacement.isDead || !destination.spawnEntityInWorld(replacement) || replacement.isDead)
            {
                replacement.isDead = true;
                return false;
            }
        }
        catch (RuntimeException exception)
        {
            if (replacement != null) replacement.isDead = true;
            MoCLog.logger.error("Unable to transfer pet to dimension " + destination.provider.dimensionId, exception);
            return false;
        }

        // Commit only after the destination accepts the replacement; failed transfers retain mounts too.
        if (entity.riddenByEntity != null) entity.riddenByEntity.mountEntity(null);
        if (entity.ridingEntity != null) entity.mountEntity(null);
        // Do not use setDead(): healthy tamed pets deliberately ignore it.
        entity.isDead = true;
        source.resetUpdateEntityTick();
        destination.resetUpdateEntityTick();
        return true;
    }
}

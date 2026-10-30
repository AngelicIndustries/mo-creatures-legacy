package drzhark.mocreatures.item;

import java.util.List;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import drzhark.mocreatures.MoCTools;
import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.utils.MoCLog;
import drzhark.mocreatures.entity.MoCEntityTameableAnimal;
import drzhark.mocreatures.entity.animal.MoCEntityHorse;
import drzhark.mocreatures.network.MoCMessageHandler;
import drzhark.mocreatures.network.message.MoCMessageAppear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class MoCItemHorseAmulet extends MoCItem {

    private MoCAmuletData legacyData;

    public MoCItemHorseAmulet(String name)
    {
        super(name);
        maxStackSize = 1;
        setHasSubtypes(true);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack itemStack, World worldObj, EntityPlayer entityPlayer)
    {
        if (worldObj.isRemote || itemStack.getItemDamage() == 0) return itemStack;
        MoCAmuletData data = new MoCAmuletData(itemStack.stackTagCompound, itemStack.getItemDamage(), true);
        if (!data.belongsTo(entityPlayer.getCommandSenderName())) return itemStack;
        ItemStack empty = emptyAmulet(data.creatureType);
        if (empty == null) return itemStack;
        ItemStack result = itemStack;
        MoCEntityTameableAnimal storedCreature = null;
        try
        {
            storedCreature = createHorse(worldObj);
            if (storedCreature == null) return itemStack;
            double angle = Math.toRadians(entityPlayer.rotationYaw - 90F);
            storedCreature.setPosition(entityPlayer.posX - 3 * Math.cos(angle), entityPlayer.posY, entityPlayer.posZ - 3 * Math.sin(angle));
            data.restoreHorse(storedCreature, entityPlayer.getCommandSenderName());
            if (data.creatureType >= 26 && data.creatureType <= 28 && storedCreature instanceof MoCEntityHorse)
            {
                // Include the fade in the spawn packet, before the client can render the horse.
                ((MoCEntityHorse)storedCreature).startAmuletAppearance();
            }
            if (storedCreature.isDead || !worldObj.spawnEntityInWorld(storedCreature) || storedCreature.isDead)
            {
                storedCreature.isDead = true;
                return itemStack;
            }
            // Consume the filled amulet before optional effects can throw.
            result = empty;
            entityPlayer.inventory.setInventorySlotContents(entityPlayer.inventory.currentItem, empty);
            MoCTools.recordAmuletState(storedCreature, false);
            onReleased(storedCreature, entityPlayer);
        }
        catch (Exception exception)
        {
            if (result == itemStack && storedCreature != null) storedCreature.isDead = true;
            MoCLog.logger.warn("Error releasing horse from amulet", exception);
        }
        return result;
    }

    protected MoCEntityTameableAnimal createHorse(World world)
    {
        return new MoCEntityHorse(world);
    }

    private ItemStack emptyAmulet(int type)
    {
        if (type == 26 || type == 27 || type == 28) return new ItemStack(MoCreatures.amuletBone, 1, 0);
        if (type == 21 || type == 22) return new ItemStack(MoCreatures.amuletGhost, 1, 0);
        if (type > 47 && type < 60) return new ItemStack(MoCreatures.amuletFairy, 1, 0);
        if (type == 39 || type == 40) return new ItemStack(MoCreatures.amuletPegasus, 1, 0);
        return null;
    }

    protected void onReleased(MoCEntityTameableAnimal storedCreature, EntityPlayer entityPlayer)
    {
        World worldObj = storedCreature.worldObj;
        MoCMessageHandler.INSTANCE.sendToAllAround(new MoCMessageAppear(storedCreature.getEntityId()), new TargetPoint(worldObj.provider.dimensionId, entityPlayer.posX, entityPlayer.posY, entityPlayer.posZ, 64));
        MoCTools.playCustomSound(storedCreature, "appearmagic", worldObj);
    }

    public void readFromNBT(NBTTagCompound nbt)
    {
        legacyData = new MoCAmuletData(nbt, 0, true);
    }

    public void writeToNBT(NBTTagCompound nbt)
    {
        if (legacyData != null) legacyData.writeToNBT(nbt);
    }

    @SideOnly(Side.CLIENT)

    /**
     * allows items to add custom lines of information to the mouseover description
     */
    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer par2EntityPlayer, List par3List, boolean par4)
    {
        MoCAmuletData data = new MoCAmuletData(itemStack.stackTagCompound, itemStack.getItemDamage(), true);
        if (data.name.length() > 0) par3List.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("entity.MoCreatures.Horse.name")); //Writes the name of the entity type to item desc
        if (data.name.length() > 0) par3List.add(EnumChatFormatting.BLUE + data.name); //writes the pet name to item desc
        if (data.ownerName.length() > 0) par3List.add(EnumChatFormatting.DARK_BLUE + ((new ChatComponentTranslation("amulet_and_fishnet_desc.MoCreatures.ownedBy", new Object[] {data.ownerName})).getUnformattedTextForChat())); //writes "owned by OWNER" (dependent on lang files)in item desc
    }
    
}

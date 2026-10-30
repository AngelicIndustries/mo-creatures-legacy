package drzhark.mocreatures.item;

import java.util.List;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import drzhark.mocreatures.MoCTools;
import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.entity.animal.MoCEntityKitty;
import drzhark.mocreatures.network.MoCMessageHandler;
import drzhark.mocreatures.network.message.MoCMessageAppear;
import drzhark.mocreatures.utils.MoCLog;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class MoCItemPetAmulet extends MoCItem
{
    private IIcon[] icons;
    private MoCAmuletData legacyData;
    private int amuletType;

    public MoCItemPetAmulet(String name) 
    {
        super(name);
        maxStackSize = 1;
        setHasSubtypes(true);
    }

    public MoCItemPetAmulet(String name, int type) 
    {
        this(name);
        amuletType = type;
    }
    
    @Override
    public ItemStack onItemRightClick(ItemStack itemStack, World worldObj, EntityPlayer entityPlayer)
    {
        if (worldObj.isRemote || itemStack.getItemDamage() == 0) return itemStack;
        MoCAmuletData data = new MoCAmuletData(itemStack.stackTagCompound, itemStack.getItemDamage(), false);
        if (data.spawnClass.isEmpty() || !data.belongsTo(entityPlayer.getCommandSenderName())) return itemStack;
        ItemStack empty = new ItemStack(amuletType == 1 ? MoCreatures.petAmulet : MoCreatures.fishNet, 1, 0);
        ItemStack result = itemStack;
        EntityLiving entity = null;
        try
        {
            entity = createPet(data.spawnClass, worldObj);
            if (!(entity instanceof IMoCTameable)) return itemStack;
            IMoCTameable pet = (IMoCTameable)entity;
            double angle = Math.toRadians(entityPlayer.rotationYaw - 90F);
            entity.setPosition(entityPlayer.posX - Math.cos(angle), entityPlayer.posY, entityPlayer.posZ - Math.sin(angle));
            data.restore(entity, entityPlayer.getCommandSenderName());
            if (entity instanceof MoCEntityKitty) ((MoCEntityKitty)entity).setKittyState(2);
            if (entity.isDead || !worldObj.spawnEntityInWorld(entity) || entity.isDead)
            {
                entity.isDead = true;
                return itemStack;
            }
            result = empty;
            entityPlayer.inventory.setInventorySlotContents(entityPlayer.inventory.currentItem, empty);
            MoCTools.recordAmuletState(pet, false);
            onReleased(entity, entityPlayer, data.ownerName.isEmpty());
        }
        catch (Exception exception)
        {
            if (result == itemStack && entity != null) entity.isDead = true;
            MoCLog.logger.warn("Error releasing creature from fishnet/amulet", exception);
        }
        return result;
    }

    protected EntityLiving createPet(String name, World world)
    {
        return MoCTools.spawnListByNameClass(name, world);
    }

    protected void onReleased(EntityLiving entity, EntityPlayer entityPlayer, boolean ownerless)
    {
        World worldObj = entity.worldObj;
        IMoCTameable pet = (IMoCTameable)entity;
        MoCMessageHandler.INSTANCE.sendToAllAround(new MoCMessageAppear(entity.getEntityId()), new TargetPoint(worldObj.provider.dimensionId, entityPlayer.posX, entityPlayer.posY, entityPlayer.posZ, 64));
        if ((MoCreatures.proxy.enableStrictOwnership && ownerless) || pet.getName().isEmpty())
        {
            MoCTools.tameWithName(entityPlayer, pet);
        }
    }

    public void readFromNBT(NBTTagCompound nbt)
    {
        legacyData = new MoCAmuletData(nbt, 0, false);
    }

    public void writeToNBT(NBTTagCompound nbt)
    {
        if (legacyData != null) legacyData.writeToNBT(nbt);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IIconRegister iconRegister)
    {
        icons = new IIcon[4];
        icons[0] = iconRegister.registerIcon("mocreatures"+ getUnlocalizedName().replaceFirst("item.", ":")); //empty fishnet
        icons[1] = iconRegister.registerIcon("mocreatures"+ getUnlocalizedName().replaceFirst("item.", ":") + "full"); //fishnet with generic fish
        icons[2] = iconRegister.registerIcon("mocreatures"+ getUnlocalizedName().replaceFirst("item.", ":")); //empty superamulet
        icons[3] = iconRegister.registerIcon("mocreatures"+ getUnlocalizedName().replaceFirst("item.", ":") + "full"); //full superamulet
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIconFromDamage(int itemDamage)
    {
        if (amuletType == 1)
        {
            if (itemDamage < 1)
            {
                return icons[2];
            }
            return icons[3];
        }
        
        if (itemDamage < 1)
        {
            return icons[0];
        }
        return icons[1];
    }
    
    @SideOnly(Side.CLIENT)

    /**
     * allows items to add custom lines of information to the mouseover description
     */
    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer par2EntityPlayer, List par3List, boolean par4)
    {
        MoCAmuletData data = new MoCAmuletData(itemStack.stackTagCompound, itemStack.getItemDamage(), false);
        if (data.spawnClass.length() > 0) par3List.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("entity.MoCreatures." + data.spawnClass + ".name"));  //Writes the name of the entity type to item desc
        if (data.name.length() > 0)    par3List.add(EnumChatFormatting.BLUE + data.name); //writes the pet name to item desc
        if (data.ownerName.length() > 0) par3List.add(EnumChatFormatting.DARK_BLUE + ((new ChatComponentTranslation("amulet_and_fishnet_desc.MoCreatures.ownedBy", new Object[] {data.ownerName})).getUnformattedTextForChat())); //writes "owned by OWNER" (dependent on lang files)in item desc
    }
    
}

package drzhark.mocreatures.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cpw.mods.fml.common.FMLCommonHandler;
import drzhark.mocreatures.MoCPetData;
import drzhark.mocreatures.MoCreatures;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class CommandMoCTP extends CommandBase {

    private static List<String> commands = new ArrayList<String>();
    private static List aliases = new ArrayList<String>();

    static {
        commands.add("/moctp <entityid> <playername>");
        commands.add("/moctp <petname> <playername>");
        aliases.add("moctp");
       //tabCompletionStrings.add("moctp");
    }

    @Override
	public String getCommandName()
    {
        return "moctp";
    }

    @Override
	public List getCommandAliases()
    {
        return aliases;
    }

    /**
     * Return the required permission level for this command.
     */
    @Override
	public int getRequiredPermissionLevel()
    {
        return 2;
    }

    @Override
	public String getCommandUsage(ICommandSender par1ICommandSender)
    {
        return "commands.moctp.usage";
    }

    @Override
	public void processCommand(ICommandSender par1ICommandSender, String[] charArray)
    {
        int petId = 0;
        if (charArray == null || charArray.length == 0)
        {
            par1ICommandSender.addChatMessage(new ChatComponentTranslation(EnumChatFormatting.RED + "Error" + EnumChatFormatting.WHITE + ": You must enter a valid entity ID."));
            return;
        }
        try 
        {
            petId = Integer.parseInt(charArray[0]);
        }
        catch (NumberFormatException e)
        {
            petId = -1;
        }
        String playername = par1ICommandSender.getCommandSenderName();
        EntityPlayerMP player = FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().func_152612_a(playername);
        // search for tamed entity in mocreatures.dat
        MoCPetData ownerPetData = MoCreatures.instance.mapData.getPetData(playername);
        if (player != null && ownerPetData != null)
        {
            for (int i = 0; i < ownerPetData.getTamedList().tagCount(); i++)
            {
                NBTTagCompound nbt = ownerPetData.getTamedList().getCompoundTagAt(i);
                if (nbt.hasKey("PetId") && nbt.getInteger("PetId") == petId)
                {
                    WorldServer world = DimensionManager.getWorld(nbt.getInteger("Dimension"));
                    MoCPetTeleport.teleportPet(world, player, nbt, par1ICommandSender);
                    break;
                }
            }
        }
        else
        {
            par1ICommandSender.addChatMessage(new ChatComponentTranslation("Tamed entity could not be located."));
        }
    }

    /**
     * Returns a sorted list of all possible commands for the given ICommandSender.
     */
    protected List getSortedPossibleCommands(ICommandSender par1ICommandSender)
    {
        Collections.sort(commands);
        return commands;
    }

    public boolean teleportLoadedPet(WorldServer world, EntityPlayerMP player, int petId, String petName, ICommandSender par1ICommandSender)
    {
        return MoCPetTeleport.teleportLoadedPet(world, player, petId, par1ICommandSender) == MoCPetTeleport.Result.SUCCESS;
    }
    public void sendCommandHelp(ICommandSender sender)
    {
        sender.addChatMessage(new ChatComponentTranslation("\u00a72Listing MoCreatures commands"));
        for (int i = 0; i < commands.size(); i++)
        {
            sender.addChatMessage(new ChatComponentTranslation(commands.get(i)));
        }
    }
}
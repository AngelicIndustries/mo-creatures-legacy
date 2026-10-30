package drzhark.mocreatures;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import drzhark.mocreatures.entity.IMoCTameable;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.DimensionManager;

public class MoCPetMapData extends WorldSavedData
{
    private Map<String, MoCPetData> petMap = new TreeMap<String, MoCPetData>(String.CASE_INSENSITIVE_ORDER);

    public MoCPetMapData(String par1Str)
    {
        super(par1Str);
        markDirty();
    }

    /**
     * Get a list of pets.
     */
    public MoCPetData getPetData(String owner)
    {
        return petMap.get(owner);
    }

    public Map<String, MoCPetData> getPetMap()
    {
        return petMap;
    }

    public void setInAmulet(IMoCTameable pet, boolean flag)
    {
        if (((Entity)pet).worldObj.isRemote) return;
        MoCPetData data = getPetData(pet.getOwnerName());
        if (data != null && data.getPetData(pet.getOwnerPetId()) != null && data.getInAmulet(pet.getOwnerPetId()) != flag)
        {
            data.setInAmulet(pet.getOwnerPetId(), flag);
            markDirty();
        }
    }

    public boolean removeOwnerPet(IMoCTameable pet, int petId)
    {
        if (petMap.get(pet.getOwnerName()) != null) // required since getInteger will always return 0 if no key is found
        {
           if (petMap.get(pet.getOwnerName()).removePet(petId))
           {
               markDirty();
               pet.setOwnerPetId(-1);
               return true;
           }
        }
        return false;
    }

    public void updateOwnerPet(IMoCTameable pet, NBTTagCompound petNBT)
    {
        if (((Entity)pet).worldObj.isRemote) return;
        markDirty();
        MoCPetData existingData = getPetData(pet.getOwnerName());
        if (pet.getOwnerPetId() < 0 || existingData == null || existingData.getPetData(pet.getOwnerPetId()) == null)
        {
            String owner = pet.getOwnerName();
            MoCPetData petData = null;
            int id = -1;
            if (petMap.containsKey(owner))
            {
                petData = petMap.get(owner);
                id = petData.addPet(pet, petNBT);
            }
            else // create new pet data
            {
                petData = new MoCPetData(pet);
                id = petData.addPet(pet, petNBT);
                petMap.put(owner, petData);
            }
            pet.setOwnerPetId(id);
        }
        else
        {
            // update pet data
            String owner = pet.getOwnerName();
            MoCPetData petData = getPetData(owner);
            NBTTagCompound rootNBT = petData.getOwnerRootNBT();
            NBTTagList tag = rootNBT.getTagList("TamedList", 10);
            int id = -1;
            id = pet.getOwnerPetId();

            for (int i = 0; i < tag.tagCount(); i++)
            {
                NBTTagCompound nbt = tag.getCompoundTagAt(i);
                if (nbt.getInteger("PetId") == id)
                {
                    //nbt = (NBTTagCompound)petNBT.copy(); //this breaks updates
                    double posX = Math.round(petNBT.getTagList("Pos", 6).func_150309_d(0));
                    double posY = Math.round(petNBT.getTagList("Pos", 6).func_150309_d(1));
                    double posZ = Math.round(petNBT.getTagList("Pos", 6).func_150309_d(2));
                    // Update what we need for commands
                    nbt.setTag("Pos", newDoubleNBTList(new double[] {posX, posY + ((Entity)pet).ySize, posZ}));
                    nbt.setInteger("ChunkX", ((Entity)pet).chunkCoordX);
                    nbt.setInteger("ChunkY", ((Entity)pet).chunkCoordY);
                    nbt.setInteger("ChunkZ", ((Entity)pet).chunkCoordZ);
                    nbt.setInteger("Dimension", ((Entity)pet).worldObj.provider.dimensionId);
                    nbt.setInteger("PetId", pet.getOwnerPetId());
                }
            }
        }
    }

    /** Update a registered pet after an accepted move, before the next entity save. */
    public void updateOwnerPetLocation(IMoCTameable pet)
    {
        Entity entity = (Entity)pet;
        if (entity.worldObj.isRemote) return;
        MoCPetData data = getPetData(pet.getOwnerName());
        if (data == null) return;
        NBTTagCompound record = data.getPetData(pet.getOwnerPetId());
        if (record == null) return;
        record.setTag("Pos", newDoubleNBTList(Math.round(entity.posX), Math.round(entity.posY) + entity.ySize, Math.round(entity.posZ)));
        // setPosition does not update chunk membership until the world ticks the entity.
        record.setInteger("ChunkX", MathHelper.floor_double(entity.posX / 16));
        record.setInteger("ChunkY", MathHelper.floor_double(entity.posY / 16));
        record.setInteger("ChunkZ", MathHelper.floor_double(entity.posZ / 16));
        record.setInteger("Dimension", entity.worldObj.provider.dimensionId);
        markDirty();
    }

    protected NBTTagList newDoubleNBTList(double ... par1ArrayOfDouble)
    {
        NBTTagList nbttaglist = new NBTTagList();
        double[] adouble = par1ArrayOfDouble;
        int i = par1ArrayOfDouble.length;

        for (int j = 0; j < i; ++j)
        {
            double d1 = adouble[j];
            nbttaglist.appendTag(new NBTTagDouble(d1));
        }

        return nbttaglist;
    }

    public boolean isExistingPet(String owner, IMoCTameable pet)
    {
        MoCPetData petData = MoCreatures.instance.mapData.getPetData(owner);
        if (petData != null)
        {
            NBTTagList tag = petData.getTamedList();
            for (int i = 0; i < tag.tagCount(); i++)
            {
                NBTTagCompound nbt = tag.getCompoundTagAt(i);
                if (nbt.getInteger("PetId") == pet.getOwnerPetId())
                {
                    // found existing pet
                    return true;
                }
            }
        }
        return false;
    }

    public void forceSave()
    {
        if (DimensionManager.getWorld(0) != null)
        {
            ISaveHandler saveHandler = DimensionManager.getWorld(0).getSaveHandler();
            if (saveHandler != null)
            {
                try
                {
                    File file1 = saveHandler.getMapFileFromName("mocreatures");

                    if (file1 != null)
                    {
                        NBTTagCompound nbtTagCompound = new NBTTagCompound();
                        writeToNBT(nbtTagCompound);
                        NBTTagCompound nbtTagCompound1 = new NBTTagCompound();
                        nbtTagCompound1.setTag("data", nbtTagCompound);
                        FileOutputStream fileoutputstream = new FileOutputStream(file1);
                        CompressedStreamTools.writeCompressed(nbtTagCompound1, fileoutputstream);
                        fileoutputstream.close();
                    }
                }
                catch (Exception exception)
                {
                    exception.printStackTrace();
                }
            }
        }
    }

    /**
     * reads in data from the NBTTagCompound into this MapDataBase
     */
    @Override
	public void readFromNBT(NBTTagCompound par1NBTTagCompound)
    {
        petMap.clear();
        Iterator iterator = par1NBTTagCompound.func_150296_c().iterator();
        while (iterator.hasNext())
        {
            String s = (String)iterator.next();
            NBTTagCompound nbt = (NBTTagCompound)par1NBTTagCompound.getTag(s);

            if (!petMap.containsKey(s))
            {
                petMap.put(s, new MoCPetData(nbt, s));
            }
        }
    }

    /**
     * write data to NBTTagCompound from this MapDataBase, similar to Entities and TileEntities
     */
    @Override
	public void writeToNBT(NBTTagCompound par1NBTTagCompound)
    {
        for (Map.Entry<String, MoCPetData> ownerEntry : petMap.entrySet())
        {
            par1NBTTagCompound.setTag(ownerEntry.getKey(), ownerEntry.getValue().getOwnerRootNBT());
        }
    }
}

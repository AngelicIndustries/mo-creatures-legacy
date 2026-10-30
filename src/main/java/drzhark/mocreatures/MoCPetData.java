package drzhark.mocreatures;

import java.util.BitSet;

import drzhark.mocreatures.entity.IMoCTameable;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;

public class MoCPetData {

    private NBTTagCompound ownerData = new NBTTagCompound();
    private NBTTagList tamedList = new NBTTagList();
    private BitSet IDMap = new BitSet(Long.SIZE << 4);
    private final String ownerName;

    public MoCPetData(IMoCTameable pet)
    {
        ownerData.setTag("TamedList", tamedList);
        ownerName = pet.getOwnerName();
        //ownerData.setName("PetData");
    }

    public MoCPetData(NBTTagCompound nbt, String owner)
    {
        ownerData = nbt;
        tamedList = nbt.getTagList("TamedList", 10);
        ownerData.setTag("TamedList", tamedList);
        ownerName = owner;
        loadPetDataMap(nbt.getCompoundTag("PetIdData"));
        ownerData.setTag("PetIdData", savePetDataMap());
    }

    public int addPet(IMoCTameable pet, NBTTagCompound petNBT)
    {
        ChunkCoordinates coords = new ChunkCoordinates(((Entity)pet).chunkCoordX, ((Entity)pet).chunkCoordY, ((Entity)pet).chunkCoordZ);
        if (tamedList != null)
        {
            int id = getNextFreePetId();
            petNBT.setInteger("PetId", id);
            NBTTagCompound petData = (NBTTagCompound)petNBT.copy();
            petData.setInteger("ChunkX", coords.posX);
            petData.setInteger("ChunkY", coords.posY);
            petData.setInteger("ChunkZ", coords.posZ);
            petData.setInteger("Dimension", ((Entity)pet).worldObj.provider.dimensionId);
            tamedList.appendTag(petData);
            ownerData.setTag("PetIdData", savePetDataMap());
            return id;
        }
        else
        {
            return -1;
        }
    }

    public boolean removePet(int id)
    {
        if (id < 0) return false;
        for (int i = 0; i < tamedList.tagCount(); i++)
        {
            NBTTagCompound nbt = tamedList.getCompoundTagAt(i);
            if (nbt.hasKey("PetId") && nbt.getInteger("PetId") == id)
            {
                tamedList.removeTag(i);
                // Old saves may already contain duplicates; do not free an ID still in use.
                if (getPetData(id) == null) IDMap.clear(id);
                ownerData.setTag("PetIdData", savePetDataMap());
                return true;
            }
        }
        return false;
    }

    public NBTTagCompound getPetData(int id)
    {
        if (tamedList != null)
        {
            for (int i = 0; i < tamedList.tagCount(); i++)
            {
                NBTTagCompound nbt = tamedList.getCompoundTagAt(i);
                if (nbt.hasKey("PetId") && nbt.getInteger("PetId") == id)
                {
                    return nbt;
                }
            }
        }
        return null;
    }

    public NBTTagCompound getOwnerRootNBT()
    {
        return ownerData;
    }

    public NBTTagList getTamedList()
    {
        return tamedList;
    }

    public String getOwner()
    {
        if (ownerData != null)
        {
            return ownerData.getString("Owner");
        }
        else return null;
    }

    public boolean getInAmulet(int petId)
    {
        NBTTagCompound petData = getPetData(petId);
        if (petData != null)
        {
            return petData.getBoolean("InAmulet");
        }
        return false;
    }

    public void setInAmulet(int petId, boolean flag)
    {
        NBTTagCompound petData = getPetData(petId);
        if (petData != null)
        {
            petData.setBoolean("InAmulet", flag);
        }
    }

    /**
     * Return the next free pet ID.
     * @return the next free pet ID
     */
    public int getNextFreePetId() {
        int next = IDMap.nextClearBit(0);
        IDMap.set(next);
        return next;
    }

    public NBTTagCompound savePetDataMap()
    {
        int[] data = new int[(IDMap.length() + Integer.SIZE - 1 )/ Integer.SIZE];
        NBTTagCompound dataMap = new NBTTagCompound();
        for (int i = 0; i < data.length; i++)
        {
            int val = 0;
            for (int j = 0; j < Integer.SIZE; j++)
            {
                val |= IDMap.get(i * Integer.SIZE + j) ? (1 << j) : 0;
            }
            data[i] = val;
        }
        dataMap.setIntArray("PetIdArray", data);
        return dataMap;
    }

    public void loadPetDataMap(NBTTagCompound compoundTag)
    {
        IDMap.clear();
        if (compoundTag != null)
        {
            int[] intArray = compoundTag.getIntArray("PetIdArray");
            for (int i = 0; i < intArray.length; i++)
            {
                for (int j = 0; j < Integer.SIZE; j++)
                {
                    IDMap.set(i * Integer.SIZE + j, (intArray[i] & (1 << j)) != 0);
                }
            }
        }
        // Legacy did not reserve the last allocated ID before saving its bitmap.
        // Recover reservations from the records too, including pets stored in amulets.
        for (int i = 0; i < tamedList.tagCount(); i++)
        {
            NBTTagCompound pet = tamedList.getCompoundTagAt(i);
            if (pet.hasKey("PetId", 3) && pet.getInteger("PetId") >= 0)
            {
                IDMap.set(pet.getInteger("PetId"));
            }
        }
    }
}

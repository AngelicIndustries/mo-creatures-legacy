package drzhark.mocreatures.item;

import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.entity.MoCEntityTameableAnimal;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;

/** A per-use snapshot; item instances are shared by every stack and both logical sides. */
final class MoCAmuletData {
    private final NBTTagCompound nbt;
    final String name;
    final String ownerName;
    final int creatureType;
    final String spawnClass;

    MoCAmuletData(NBTTagCompound source, int damage, boolean horse)
    {
        nbt = source == null ? new NBTTagCompound() : (NBTTagCompound)source.copy();
        int petId = nbt.hasKey("PetId", 3) ? nbt.getInteger("PetId")
                : nbt.hasKey("PetID", 3) ? nbt.getInteger("PetID") : -1;
        nbt.removeTag("PetID");
        nbt.setInteger("PetId", Math.max(-1, petId));
        if (horse && nbt.getInteger("CreatureType") == 0)
        {
            nbt.setInteger("CreatureType", damage);
            if (!nbt.hasKey("Age")) nbt.setInteger("Age", 100);
            if (!nbt.hasKey("Adult")) nbt.setBoolean("Adult", true);
        }
        name = nbt.getString("Name");
        ownerName = nbt.getString("OwnerName");
        creatureType = nbt.getInteger("CreatureType");
        String storedClass = nbt.getString("SpawnClass");
        spawnClass = "MoCHorse".equalsIgnoreCase(storedClass) ? "Horse" : storedClass;
    }

    boolean belongsTo(String playerName)
    {
        return ownerName.isEmpty() || ownerName.equals(playerName);
    }

    void restore(EntityLiving entity, String playerName)
    {
        IMoCTameable pet = (IMoCTameable)entity;
        pet.setType(creatureType);
        pet.setTamed(true);
        pet.setName(name);
        pet.setOwnerPetId(ownerName.isEmpty() ? -1 : nbt.getInteger("PetId"));
        pet.setOwner(playerName);
        pet.setMoCAge(nbt.getInteger("Age"));
        pet.setAdult(nbt.getBoolean("Adult"));
        float maxHealth = nbt.getFloat("MaxHealth");
        if (validHealth(maxHealth)) entity.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(maxHealth);
        float health = nbt.getFloat("Health");
        entity.setHealth(validHealth(health) ? health : entity.getMaxHealth());
    }

    void restoreHorse(MoCEntityTameableAnimal horse, String playerName)
    {
        restore(horse, playerName);
        horse.setRideable(nbt.getBoolean("Rideable"));
        horse.setArmorType(nbt.getByte("Armor"));
    }

    private static boolean validHealth(float health)
    {
        return health > 0 && !Float.isInfinite(health) && !Float.isNaN(health);
    }

    void writeToNBT(NBTTagCompound target)
    {
        target.removeTag("PetID");
        for (Object key : nbt.func_150296_c())
        {
            String name = (String)key;
            target.setTag(name, nbt.getTag(name).copy());
        }
    }
}

package drzhark.mocreatures;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import drzhark.mocreatures.entity.IMoCTameable;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class PetPersistenceTest {
    @Test
    public void allocationReservesItsIdBeforeSaving() throws Exception
    {
        MoCPetData data = owner();
        int id = data.getNextFreePetId();
        assertEquals(0, id);
        assertEquals(1, data.savePetDataMap().getIntArray("PetIdArray")[0]);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("PetIdData", data.savePetDataMap());
        MoCPetData loaded = new MoCPetData(roundTrip(root), "Owner");
        assertNotEquals(id, loaded.getNextFreePetId());
    }

    @Test
    public void repairsLegacyBitmapWithDeletedHoleAndUnreservedLastPet() throws Exception
    {
        // Legacy saved the bit for ID 1, but not its most recently allocated ID 2.
        NBTTagCompound root = rootWithPets(1, 2);
        NBTTagCompound bitmap = new NBTTagCompound();
        bitmap.setIntArray("PetIdArray", new int[] { 2 });
        root.setTag("PetIdData", bitmap);
        MoCPetData loaded = new MoCPetData(roundTrip(root), "Owner");
        assertEquals(0, loaded.getNextFreePetId());
        assertEquals(3, loaded.getNextFreePetId());
        assertEquals(1, loaded.getPetData(1).getInteger("PetId"));
        assertEquals(2, loaded.getPetData(2).getInteger("PetId"));
    }

    @Test
    public void missingBitmapRecoversIdsFromPetRecordsAcrossWordBoundaries() throws Exception
    {
        int[] ids = { 0, 2, 31, 32, 63, 64 };
        MoCPetData loaded = new MoCPetData(roundTrip(rootWithPets(ids)), "Owner");
        Set<Integer> allocated = new HashSet<Integer>();
        for (int id : ids) allocated.add(id);
        for (int i = 0; i < 100; i++) assertTrue(allocated.add(loaded.getNextFreePetId()));
    }

    @Test
    public void addRemoveAndRepeatedReloadsKeepIdsUnique() throws Exception
    {
        MoCPetData data = owner();
        Entity entity = pet("Owner", -1);
        for (int i = 0; i < 70; i++) data.addPet((IMoCTameable) entity, new NBTTagCompound());
        assertTrue(data.removePet(0));
        assertTrue(data.removePet(32));
        assertFalse(data.removePet(999));
        for (int cycle = 0; cycle < 3; cycle++)
        {
            data = new MoCPetData(roundTrip(data.getOwnerRootNBT()), "Owner");
            data.addPet((IMoCTameable) entity, new NBTTagCompound());
            Set<Integer> ids = new HashSet<Integer>();
            for (int i = 0; i < data.getTamedList().tagCount(); i++)
                assertTrue(ids.add(data.getTamedList().getCompoundTagAt(i).getInteger("PetId")));
        }
    }

    @Test
    public void missingTamedListIsAttachedWhenFirstPetIsAdded() throws Exception
    {
        MoCPetData data = new MoCPetData(new NBTTagCompound(), "Owner");
        int id = data.addPet((IMoCTameable) pet("Owner", -1), new NBTTagCompound());
        MoCPetData loaded = new MoCPetData(roundTrip(data.getOwnerRootNBT()), "Owner");
        assertNotNull(loaded.getPetData(id));
    }

    @Test
    public void reloadingIdMapReplacesPreviousReservations()
    {
        MoCPetData data = owner();
        NBTTagCompound bitmap = new NBTTagCompound();
        bitmap.setIntArray("PetIdArray", new int[] { -1, -1 });
        data.loadPetDataMap(bitmap);
        data.loadPetDataMap(new NBTTagCompound());
        assertEquals(0, data.getNextFreePetId());
        data.loadPetDataMap(null);
        assertEquals(0, data.getNextFreePetId());
    }

    @Test
    public void removingADuplicateRecordDoesNotReleaseTheOtherPetsId()
    {
        MoCPetData data = owner(0, 0);
        assertTrue(data.removePet(0));
        assertEquals(1, data.getNextFreePetId());
        assertFalse(data.removePet(-1));
        assertNotNull(data.getPetData(0));
    }

    @Test
    public void firstSaveAndRemovalKeepOwnersAndEntityIdsSeparate() throws Exception
    {
        MoCPetMapData map = new MoCPetMapData("test");
        Entity first = pet("Alice", -1);
        Entity second = pet("Bob", -1);
        NBTTagCompound firstNbt = new NBTTagCompound();
        NBTTagCompound secondNbt = new NBTTagCompound();
        map.updateOwnerPet((IMoCTameable) first, firstNbt);
        map.updateOwnerPet((IMoCTameable) second, secondNbt);
        assertEquals(0, firstNbt.getInteger("PetId"));
        assertEquals(0, secondNbt.getInteger("PetId"));
        assertEquals(0, ((IMoCTameable) first).getOwnerPetId());
        map.setDirty(false);
        assertTrue(map.removeOwnerPet((IMoCTameable) first, 0));
        assertEquals(-1, ((IMoCTameable) first).getOwnerPetId());
        assertTrue(map.isDirty());
        NBTTagCompound saved = new NBTTagCompound();
        map.writeToNBT(saved);
        MoCPetMapData loaded = new MoCPetMapData("test");
        loaded.readFromNBT(roundTrip(saved));
        assertNull(loaded.getPetData("Alice").getPetData(0));
        assertNotNull(loaded.getPetData("Bob").getPetData(0));
        assertEquals(0, loaded.getPetData("Alice").getNextFreePetId());
        assertEquals(1, loaded.getPetData("Bob").getNextFreePetId());
    }

    @Test
    public void mapReloadReplacesOwnersAndTheirRecords() throws Exception
    {
        MoCPetMapData map = new MoCPetMapData("test");
        NBTTagCompound first = new NBTTagCompound();
        first.setTag("Owner", rootWithPets(1));
        first.setTag("PreviousWorldOwner", rootWithPets(0));
        map.readFromNBT(first);
        NBTTagCompound second = new NBTTagCompound();
        second.setTag("Owner", rootWithPets(2));
        map.readFromNBT(roundTrip(second));
        assertNull(map.getPetData("PreviousWorldOwner"));
        assertNull(map.getPetData("Owner").getPetData(1));
        assertNotNull(map.getPetData("owner").getPetData(2));
    }

    @Test
    public void missingRecordIsRecreatedAndWrittenBackToEntityNbt() throws Exception
    {
        MoCPetMapData map = new MoCPetMapData("test");
        map.getPetMap().put("Owner", owner(0));
        Entity entity = pet("Owner", 9);
        NBTTagCompound entityNbt = new NBTTagCompound();
        entityNbt.setInteger("PetId", 9);
        entityNbt.setString("Name", "Recovered pet");
        map.setDirty(false);
        map.updateOwnerPet((IMoCTameable) entity, entityNbt);
        int assignedId = ((IMoCTameable) entity).getOwnerPetId();
        assertNotEquals(0, assignedId);
        assertEquals(assignedId, entityNbt.getInteger("PetId"));
        assertTrue(map.isDirty());
        NBTTagCompound saved = new NBTTagCompound();
        map.writeToNBT(saved);
        MoCPetMapData loaded = new MoCPetMapData("test");
        loaded.readFromNBT(roundTrip(saved));
        assertEquals("Recovered pet", loaded.getPetData("Owner").getPetData(assignedId).getString("Name"));
        assertEquals(2, loaded.getPetData("Owner").getTamedList().tagCount());
    }

    @Test
    public void movingDimensionsPreservesIdAndAmuletMetadata() throws Exception
    {
        MoCPetMapData map = new MoCPetMapData("test");
        MoCPetData data = owner(7);
        data.setInAmulet(7, true);
        data.getPetData(7).setString("Name", "Traveler");
        map.getPetMap().put("Owner", data);
        Entity entity = pet("Owner", 7);
        entity.worldObj.provider.dimensionId = -17;
        entity.chunkCoordX = 12;
        entity.chunkCoordZ = -4;
        NBTTagCompound entityNbt = new NBTTagCompound();
        NBTTagList pos = new NBTTagList();
        for (double coordinate : new double[] { 192, 80, -64 }) pos.appendTag(new NBTTagDouble(coordinate));
        entityNbt.setTag("Pos", pos);
        map.setDirty(false);
        map.updateOwnerPet((IMoCTameable) entity, entityNbt);
        assertTrue(map.isDirty());
        MoCPetData loaded = new MoCPetData(roundTrip(data.getOwnerRootNBT()), "Owner");
        NBTTagCompound record = loaded.getPetData(7);
        assertEquals(-17, record.getInteger("Dimension"));
        assertEquals(12, record.getInteger("ChunkX"));
        assertEquals(-4, record.getInteger("ChunkZ"));
        assertEquals("Traveler", record.getString("Name"));
        assertTrue(record.getBoolean("InAmulet"));
        assertEquals(1, loaded.getTamedList().tagCount());
    }

    @Test
    public void clientSerializationDoesNotChangeServerPetRecords()
    {
        MoCPetMapData map = new MoCPetMapData("test");
        Entity entity = pet("Owner", -1);
        entity.worldObj.isRemote = true;
        map.setDirty(false);
        map.updateOwnerPet((IMoCTameable) entity, new NBTTagCompound());
        assertTrue(map.getPetMap().isEmpty());
        assertFalse(map.isDirty());
        assertEquals(-1, ((IMoCTameable) entity).getOwnerPetId());
    }

    private static MoCPetData owner(int... ids)
    {
        return new MoCPetData(rootWithPets(ids), "Owner");
    }

    private static NBTTagCompound rootWithPets(int... ids)
    {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList pets = new NBTTagList();
        for (int id : ids)
        {
            NBTTagCompound pet = new NBTTagCompound();
            pet.setInteger("PetId", id);
            pets.appendTag(pet);
        }
        root.setTag("TamedList", pets);
        return root;
    }

    private static Entity pet(String owner, int id)
    {
        Entity entity = mock(Entity.class, withSettings().extraInterfaces(IMoCTameable.class));
        entity.worldObj = mock(World.class);
        try
        {
            java.lang.reflect.Field provider = World.class.getField("provider");
            provider.setAccessible(true);
            provider.set(entity.worldObj, mock(WorldProvider.class));
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
        IMoCTameable pet = (IMoCTameable) entity;
        AtomicInteger petId = new AtomicInteger(id);
        when(pet.getOwnerName()).thenReturn(owner);
        when(pet.getOwnerPetId()).thenAnswer(invocation -> petId.get());
        doAnswer(invocation -> { petId.set(invocation.getArgument(0)); return null; }).when(pet).setOwnerPetId(anyInt());
        return entity;
    }

    private static NBTTagCompound roundTrip(NBTTagCompound nbt) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(nbt, bytes);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
    }
}

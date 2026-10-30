package drzhark.mocreatures.command;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.MoCPetData;
import drzhark.mocreatures.MoCPetMapData;
import drzhark.mocreatures.entity.IMoCTameable;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class PetTeleportTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> commands()
    {
        return Arrays.asList(new Object[][] { { "moctp" }, { "mocpets" } });
    }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private MoCPetMapData registry;
    private ISaveHandler save;
    private final String command;
    private WorldServer source;
    private WorldServer destination;
    private EntityPlayerMP player;
    private ICommandSender sender;
    private TestPet pet;
    private Entity rider;

    public PetTeleportTest(String command) { this.command = command; }

    @Before
    public void setup() throws Exception
    {
        source = world(0);
        destination = world(-17);
        player = mock(EntityPlayerMP.class);
        player.worldObj = destination;
        player.dimension = -17;
        player.posX = 100.5;
        player.posY = 80;
        player.posZ = -40.5;
        when(player.getCommandSenderName()).thenReturn("Owner");
        sender = mock(ICommandSender.class);
        TestPet.failCopy = false;
        TestPet.deadCopy = false;
        pet = new TestPet(source);
        pet.setPosition(4, 65, 8);
        pet.payload.setString("InventoryMarker", "saved chest contents");
        source.loadedEntityList.add(pet);
        rider = spy(new TestPet(source));
        pet.riddenByEntity = rider;
        rider.ridingEntity = pet;
        EntityList.classToStringMapping.put(TestPet.class, "MoCTestTeleportPet");
        EntityList.stringToClassMapping.put("MoCTestTeleportPet", TestPet.class);
        TestPet.lastCreated = null;
        when(destination.spawnEntityInWorld(any(Entity.class))).thenReturn(true);
        MoCreatures.instance = new MoCreatures();
        registry = new MoCPetMapData("mocreatures");
        MoCreatures.instance.mapData = registry;
        NBTTagCompound owner = new NBTTagCompound();
        NBTTagList pets = new NBTTagList();
        pets.appendTag(record());
        owner.setTag("TamedList", pets);
        registry.getPetMap().put("Owner", new MoCPetData(owner, "Owner"));
        File folder = temporary.newFolder();
        save = mock(ISaveHandler.class);
        when(save.getMapFileFromName(anyString())).thenAnswer(invocation -> new File(folder, invocation.getArgument(0) + ".dat"));
        source.mapStorage = new MapStorage(save);
        destination.mapStorage = source.mapStorage;
        source.mapStorage.setData("mocreatures", registry);
        source.mapStorage.saveAllData();
    }

    @After
    public void cleanup()
    {
        MoCreatures.instance = null;
        EntityList.classToStringMapping.remove(TestPet.class);
        EntityList.stringToClassMapping.remove("MoCTestTeleportPet");
        TestPet.lastCreated = null;
        TestPet.failCopy = false;
        TestPet.deadCopy = false;
    }

    @Test
    public void failedCreationPreservesPetAndRider()
    {
        EntityList.stringToClassMapping.remove("MoCTestTeleportPet");
        assertFalse(teleport(source));
        assertUnchanged();
        verify(destination, never()).spawnEntityInWorld(any(Entity.class));
    }

    @Test
    public void canceledSpawnPreservesPetAndRider()
    {
        when(destination.spawnEntityInWorld(any(Entity.class))).thenReturn(false);
        assertFalse(teleport(source));
        assertUnchanged();
        assertTrue(TestPet.lastCreated.isDead);
        verify(destination, times(1)).spawnEntityInWorld(any(Entity.class));
    }

    @Test
    public void copyAndSpawnExceptionsPreserveTheOriginal()
    {
        TestPet.failCopy = true;
        assertFalse(teleport(source));
        assertUnchanged();
        assertTrue(TestPet.lastCreated.isDead);
        verify(destination, never()).spawnEntityInWorld(any(Entity.class));
        TestPet.failCopy = false;
        when(destination.spawnEntityInWorld(any(Entity.class))).thenThrow(new IllegalStateException("test spawn failure"));
        assertFalse(teleport(source));
        assertUnchanged();
        assertTrue(TestPet.lastCreated.isDead);
    }

    @Test
    public void deadReplacementIsNotSpawned()
    {
        TestPet.deadCopy = true;
        assertFalse(teleport(source));
        assertUnchanged();
        verify(destination, never()).spawnEntityInWorld(any(Entity.class));
    }

    @Test
    public void replacementKilledDuringSpawnPreservesTheOriginal()
    {
        when(destination.spawnEntityInWorld(any(Entity.class))).thenAnswer(invocation -> {
            ((Entity)invocation.getArgument(0)).isDead = true;
            return true;
        });
        assertFalse(teleport(source));
        assertUnchanged();
    }

    @Test
    public void unmountedPetTransfersWithoutCreatingMounts()
    {
        pet.riddenByEntity = null;
        rider.ridingEntity = null;
        assertTrue(teleport(source));
        assertTrue(pet.isDead);
        assertNull(TestPet.lastCreated.ridingEntity);
        assertNull(TestPet.lastCreated.riddenByEntity);
        verify(rider, never()).mountEntity(any());
    }

    @Test
    public void missingWorldsAndClientWorldsDoNotMutateThePet()
    {
        assertFalse(teleport(null));
        player.worldObj = null;
        assertFalse(teleport(source));
        player.worldObj = destination;
        destination.isRemote = true;
        assertFalse(teleport(source));
        destination.isRemote = false;
        source.isRemote = true;
        assertFalse(teleport(source));
        assertUnchanged();
        verify(destination, never()).spawnEntityInWorld(any(Entity.class));
    }

    @Test
    public void successfulTransferPreservesNbtAndCommitsAfterSpawn()
    {
        when(destination.spawnEntityInWorld(any(Entity.class))).thenAnswer(invocation -> {
            assertFalse(pet.isDead);
            assertEquals(0, registry.getPetData("Owner").getPetData(7).getInteger("Dimension"));
            assertSame(rider, pet.riddenByEntity);
            verify(rider, never()).mountEntity(any());
            return true;
        });
        assertTrue(teleport(source));
        TestPet replacement = TestPet.lastCreated;
        assertTrue(pet.isDead);
        assertFalse(replacement.isDead);
        assertSame(destination, replacement.worldObj);
        assertEquals(-17, replacement.dimension);
        assertEquals(player.posX, replacement.posX, 0);
        assertEquals(player.posY, replacement.posY, 0);
        assertEquals(player.posZ, replacement.posZ, 0);
        assertEquals(pet.getUniqueID(), replacement.getUniqueID());
        assertEquals(pet.getOwnerPetId(), replacement.getOwnerPetId());
        assertEquals(pet.getOwnerName(), replacement.getOwnerName());
        assertEquals(pet.getName(), replacement.getName());
        assertEquals(pet.payload, replacement.payload);
        verify(rider).mountEntity(null);
        assertNull(pet.riddenByEntity);
        assertNull(rider.ridingEntity);
        verify(source).resetUpdateEntityTick();
        verify(destination).resetUpdateEntityTick();
        verify(sender).addChatMessage(any(IChatComponent.class));
    }

    @Test
    public void sameWorldMoveDoesNotReplaceTheEntity()
    {
        player.worldObj = source;
        player.dimension = 0;
        assertTrue(teleport(source));
        assertFalse(pet.isDead);
        assertNull(TestPet.lastCreated);
        assertEquals(player.posX, pet.posX, 0);
        verify(source, never()).spawnEntityInWorld(any(Entity.class));
        assertRegistryLocation(0);
    }

    @Test
    public void petsRidingOtherEntitiesDismountOnlyOnSuccess()
    {
        Entity mount = new TestPet(source);
        pet.ridingEntity = mount;
        mount.riddenByEntity = pet;
        when(destination.spawnEntityInWorld(any(Entity.class))).thenReturn(false);
        assertFalse(teleport(source));
        assertSame(mount, pet.ridingEntity);
        assertSame(pet, mount.riddenByEntity);
        when(destination.spawnEntityInWorld(any(Entity.class))).thenReturn(true);
        assertTrue(teleport(source));
        assertNull(pet.ridingEntity);
        assertNull(mount.riddenByEntity);
    }

    @Test
    public void otherOwnersAndDeadOrUntamedPetsAreIgnored()
    {
        pet.owner = "SomeoneElse";
        assertFalse(teleport(source));
        pet.owner = "Owner";
        pet.tamed = false;
        assertFalse(teleport(source));
        pet.tamed = true;
        pet.isDead = true;
        assertFalse(teleport(source));
        verify(destination, never()).spawnEntityInWorld(any(Entity.class));
        verifyNoInteractions(sender);
    }

    @Test
    public void registryLookupDoesNotRetryACanceledTransfer()
    {
        when(destination.spawnEntityInWorld(any(Entity.class))).thenReturn(false);
        MoCPetTeleport.teleportPet(source, player, record(), sender);
        verify(destination, times(1)).spawnEntityInWorld(any(Entity.class));
        verify(source, never()).getChunkFromChunkCoords(anyInt(), anyInt());
        assertFalse(pet.isDead);
        verify(sender).addChatMessage(argThat(message -> message.getUnformattedText().contains("has not been moved")));
    }

    @Test
    public void registryLookupReportsUnloadedDimension()
    {
        MoCPetTeleport.teleportPet(null, player, record(), sender);
        assertFalse(pet.isDead);
        verifyNoInteractions(destination);
        verify(sender).addChatMessage(argThat(message -> message.getUnformattedText().contains("dimension is not loaded")));
    }

    @Test
    public void registryLookupLoadsMissingPetsChunkOnce()
    {
        NBTTagCompound record = record();
        source.loadedEntityList.clear();
        when(source.getChunkFromChunkCoords(0, 0)).thenAnswer(invocation -> {
            source.loadedEntityList.add(pet);
            return null;
        });
        MoCPetTeleport.teleportPet(source, player, record, sender);
        verify(source, times(1)).getChunkFromChunkCoords(0, 0);
        verify(destination, times(1)).spawnEntityInWorld(any(Entity.class));
        assertTrue(pet.isDead);
    }

    @Test
    public void destinationIsRegisteredImmediatelyAndInTheFirstSave()
    {
        assertTrue(teleport(source));
        assertRegistryLocation(-17);
        source.mapStorage.saveAllData();
        MoCPetMapData loaded = (MoCPetMapData)new MapStorage(save).loadData(MoCPetMapData.class, "mocreatures");
        assertEquals(-17, loaded.getPetData("Owner").getPetData(7).getInteger("Dimension"));
        assertEquals(101, loaded.getPetData("Owner").getPetData(7).getTagList("Pos", 6).func_150309_d(0), 0);
    }

    @Test
    public void immediateReturnTransferUpdatesTheSameRecord()
    {
        assertTrue(teleport(source));
        destination.loadedEntityList.add(TestPet.lastCreated);
        player.worldObj = source;
        player.dimension = 0;
        player.posX = -0.25;
        player.posZ = -16.25;
        when(source.spawnEntityInWorld(any(Entity.class))).thenReturn(true);
        assertTrue(teleport(destination));
        assertRegistryLocation(0);
        assertEquals(1, registry.getPetData("Owner").getTamedList().tagCount());
        assertEquals(pet.getUniqueID(), TestPet.lastCreated.getUniqueID());
    }

    private void assertRegistryLocation(int dimension)
    {
        NBTTagCompound record = registry.getPetData("Owner").getPetData(7);
        assertEquals(dimension, record.getInteger("Dimension"));
        assertEquals(Math.round(player.posX), record.getTagList("Pos", 6).func_150309_d(0), 0);
        assertEquals(Math.round(player.posY), record.getTagList("Pos", 6).func_150309_d(1), 0);
        assertEquals(Math.round(player.posZ), record.getTagList("Pos", 6).func_150309_d(2), 0);
        assertEquals((int)Math.floor(player.posX / 16), record.getInteger("ChunkX"));
        assertEquals((int)Math.floor(player.posY / 16), record.getInteger("ChunkY"));
        assertEquals((int)Math.floor(player.posZ / 16), record.getInteger("ChunkZ"));
        assertEquals("Pet", record.getString("Name"));
        assertEquals("Owner", record.getString("Owner"));
        assertEquals("saved chest contents", record.getCompoundTag("Payload").getString("InventoryMarker"));
        assertTrue(registry.isDirty());
    }

    private boolean teleport(WorldServer world)
    {
        return "moctp".equals(command)
                ? new CommandMoCTP().teleportLoadedPet(world, player, 7, "Pet", sender)
                : new CommandMoCPets().teleportLoadedPet(world, player, 7, "Pet", sender);
    }

    private NBTTagCompound record()
    {
        NBTTagCompound nbt = new NBTTagCompound();
        pet.writeToNBT(nbt);
        return nbt;
    }

    private void assertUnchanged()
    {
        assertFalse(pet.isDead);
        NBTTagCompound record = registry.getPetData("Owner").getPetData(7);
        assertEquals(0, record.getInteger("Dimension"));
        assertEquals(4, record.getTagList("Pos", 6).func_150309_d(0), 0);
        assertSame(source, pet.worldObj);
        assertEquals(0, pet.dimension);
        assertEquals(4, pet.posX, 0);
        assertSame(rider, pet.riddenByEntity);
        verify(rider, never()).mountEntity(any());
        verify(source, never()).resetUpdateEntityTick();
        verifyNoInteractions(sender);
    }

    private static WorldServer world(int dimension) throws Exception
    {
        WorldServer world = mock(WorldServer.class);
        WorldProvider provider = mock(WorldProvider.class);
        provider.dimensionId = dimension;
        setField(world, "provider", provider);
        setField(world, "loadedEntityList", new ArrayList<Entity>());
        return world;
    }

    private static void setField(World world, String name, Object value) throws Exception
    {
        Field field = World.class.getField(name);
        field.setAccessible(true);
        field.set(world, value);
    }

    public static class TestPet extends Entity implements IMoCTameable {
        static TestPet lastCreated;
        static boolean failCopy;
        static boolean deadCopy;
        String owner = "Owner";
        String name = "Pet";
        int petId = 7;
        boolean tamed = true;
        NBTTagCompound payload = new NBTTagCompound();

        public TestPet(World world) { super(world); lastCreated = this; }
        @Override protected void entityInit() {}
        @Override public void copyDataFrom(Entity entity, boolean unused) {
            if (failCopy) throw new IllegalStateException("test copy failure");
            super.copyDataFrom(entity, unused);
            isDead = deadCopy;
        }
        @Override public void writeEntityToNBT(NBTTagCompound nbt) {
            nbt.setString("Owner", owner);
            nbt.setString("Name", name);
            nbt.setInteger("PetId", petId);
            nbt.setBoolean("Tamed", tamed);
            nbt.setTag("Payload", payload.copy());
        }
        @Override public void readEntityFromNBT(NBTTagCompound nbt) {
            owner = nbt.getString("Owner");
            name = nbt.getString("Name");
            petId = nbt.getInteger("PetId");
            tamed = nbt.getBoolean("Tamed");
            payload = nbt.getCompoundTag("Payload");
        }
        @Override public String getOwnerName() { return owner; }
        @Override public String getName() { return name; }
        @Override public int getOwnerPetId() { return petId; }
        @Override public boolean getIsTamed() { return tamed; }
        @Override public void setOwner(String value) { owner = value; }
        @Override public void setName(String value) { name = value; }
        @Override public void setOwnerPetId(int value) { petId = value; }
        @Override public void setTamed(boolean value) { tamed = value; }
        @Override public boolean isRiderDisconnecting() { return false; }
        @Override public void playTameEffect(boolean value) {}
        @Override public int getType() { return 0; }
        @Override public int getMoCAge() { return 0; }
        @Override public boolean getIsAdult() { return true; }
        @Override public void setType(int value) {}
        @Override public void setMoCAge(int value) {}
        @Override public void setAdult(boolean value) {}
        @Override public float getPetHealth() { return 20; }
    }
}

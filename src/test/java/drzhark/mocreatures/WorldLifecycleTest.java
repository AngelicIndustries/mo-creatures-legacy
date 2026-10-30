package drzhark.mocreatures;

import java.io.File;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.event.world.WorldEvent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WorldLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private MoCEventHooks hooks;

    @Before
    public void setup()
    {
        MoCreatures.instance = new MoCreatures();
        MoCreatures.proxy = mock(MoCProxy.class);
        hooks = new MoCEventHooks();
    }

    @After
    public void teardown()
    {
        MoCreatures.instance = null;
        MoCreatures.proxy = null;
    }

    @Test
    public void serverOverworldOwnsRegistryAndClientEventsCannotChangeIt() throws Exception
    {
        World server = world(0, false, temporary.newFolder());
        hooks.onWorldLoad(new WorldEvent.Load(server));
        MoCPetMapData data = MoCreatures.instance.mapData;
        assertNotNull(data);
        assertTrue(MoCreatures.proxy.worldInitDone);
        assertSame(data, server.mapStorage.loadData(MoCPetMapData.class, "mocreatures"));
        World client = world(0, true, temporary.newFolder());
        hooks.onWorldLoad(new WorldEvent.Load(client));
        hooks.onWorldUnload(new WorldEvent.Unload(client));
        assertSame(data, MoCreatures.instance.mapData);
        assertTrue(MoCreatures.proxy.worldInitDone);
        verify(client.mapStorage, never()).loadData(any(), anyString());
        verify(client.mapStorage, never()).saveAllData();
    }

    @Test
    public void dimensionLoadsAndUnloadsDoNotReplaceOrClearRegistry() throws Exception
    {
        World nether = world(-1, false, temporary.newFolder());
        hooks.onWorldLoad(new WorldEvent.Load(nether));
        assertNull(MoCreatures.instance.mapData);
        assertFalse(MoCreatures.proxy.worldInitDone);
        World overworld = world(0, false, temporary.newFolder());
        hooks.onWorldLoad(new WorldEvent.Load(overworld));
        MoCPetMapData data = MoCreatures.instance.mapData;
        hooks.onWorldLoad(new WorldEvent.Load(nether));
        hooks.onWorldUnload(new WorldEvent.Unload(nether));
        assertSame(data, MoCreatures.instance.mapData);
        assertTrue(MoCreatures.proxy.worldInitDone);
        verify(nether.mapStorage, never()).loadData(any(), anyString());
    }

    @Test
    public void duplicateLoadDoesNotReplaceOrResaveTheRegistry() throws Exception
    {
        World world = world(0, false, temporary.newFolder());
        hooks.onWorldLoad(new WorldEvent.Load(world));
        MoCPetMapData data = MoCreatures.instance.mapData;
        hooks.onWorldLoad(new WorldEvent.Load(world));
        assertSame(data, MoCreatures.instance.mapData);
        verify(world.mapStorage, times(1)).loadData(MoCPetMapData.class, "mocreatures");
        verify(world.mapStorage, times(1)).saveAllData();
    }

    @Test
    public void unloadClearsBothReferencesButDoesNotDeleteSavedPets() throws Exception
    {
        File folder = temporary.newFolder();
        World world = world(0, false, folder);
        hooks.onWorldLoad(new WorldEvent.Load(world));
        addPet("Owner", 7);
        world.mapStorage.saveAllData();
        hooks.onWorldUnload(new WorldEvent.Unload(world));
        assertNull(MoCreatures.instance.mapData);
        assertFalse(MoCreatures.proxy.worldInitDone);
        assertNotNull(world.mapStorage.loadData(MoCPetMapData.class, "mocreatures"));
        hooks.onWorldLoad(new WorldEvent.Load(world(0, false, folder)));
        assertNotNull(MoCreatures.instance.mapData.getPetData("Owner").getPetData(7));
    }

    @Test
    public void switchingWorldsAndReturningKeepsEachWorldsPetsSeparate() throws Exception
    {
        File firstFolder = temporary.newFolder();
        World first = world(0, false, firstFolder);
        hooks.onWorldLoad(new WorldEvent.Load(first));
        addPet("FirstOwner", 7);
        first.mapStorage.saveAllData();
        hooks.onWorldUnload(new WorldEvent.Unload(first));
        World second = world(0, false, temporary.newFolder());
        hooks.onWorldLoad(new WorldEvent.Load(second));
        assertNull(MoCreatures.instance.mapData.getPetData("FirstOwner"));
        addPet("SecondOwner", 3);
        second.mapStorage.saveAllData();
        hooks.onWorldUnload(new WorldEvent.Unload(second));
        hooks.onWorldLoad(new WorldEvent.Load(world(0, false, firstFolder)));
        assertNotNull(MoCreatures.instance.mapData.getPetData("FirstOwner").getPetData(7));
        assertNull(MoCreatures.instance.mapData.getPetData("SecondOwner"));
    }

    @Test
    public void lateUnloadFromOldWorldCannotClearANewerWorld() throws Exception
    {
        World first = world(0, false, temporary.newFolder());
        World second = world(0, false, temporary.newFolder());
        hooks.onWorldLoad(new WorldEvent.Load(first));
        MoCPetMapData firstData = MoCreatures.instance.mapData;
        hooks.onWorldLoad(new WorldEvent.Load(second));
        MoCPetMapData secondData = MoCreatures.instance.mapData;
        assertNotSame(firstData, secondData);
        hooks.onWorldUnload(new WorldEvent.Unload(first));
        assertSame(secondData, MoCreatures.instance.mapData);
        assertTrue(MoCreatures.proxy.worldInitDone);
    }

    private void addPet(String owner, int id)
    {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound pet = new NBTTagCompound();
        pet.setInteger("PetId", id);
        NBTTagList pets = new NBTTagList();
        pets.appendTag(pet);
        root.setTag("TamedList", pets);
        MoCreatures.instance.mapData.getPetMap().put(owner, new MoCPetData(root, owner));
        MoCreatures.instance.mapData.markDirty();
    }

    private World world(int dimension, boolean remote, File folder) throws Exception
    {
        ISaveHandler save = mock(ISaveHandler.class);
        when(save.getMapFileFromName(anyString())).thenAnswer(invocation -> new File(folder, invocation.getArgument(0) + ".dat"));
        World world = mock(World.class);
        world.isRemote = remote;
        WorldProvider provider = mock(WorldProvider.class);
        provider.dimensionId = dimension;
        Field field = World.class.getField("provider");
        field.setAccessible(true);
        field.set(world, provider);
        world.mapStorage = spy(new MapStorage(save));
        return world;
    }
}

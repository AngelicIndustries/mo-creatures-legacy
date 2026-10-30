package drzhark.mocreatures;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.IEventListener;
import cpw.mods.fml.common.eventhandler.ListenerList;
import drzhark.mocreatures.command.PetTeleportTest.TestPet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class WorldSavePersistenceTest {
    @Parameterized.Parameters(name = "dimension {0}")
    public static Collection<Object[]> dimensions() {
        return Arrays.asList(new Object[][] { { 0 }, { -1 } });
    }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final int dimension;
    private MoCEventHooks hooks;
    private World overworld;
    private ISaveHandler save;
    private int busId;
    private IEventListener saveListener;

    public WorldSavePersistenceTest(int dimension) { this.dimension = dimension; }

    @Before
    public void setup() throws Exception
    {
        MoCreatures.instance = new MoCreatures();
        MoCreatures.proxy = mock(MoCProxy.class);
        hooks = new MoCEventHooks();
        overworld = mock(World.class);
        setProvider(overworld, 0);
        File folder = temporary.newFolder();
        save = mock(ISaveHandler.class);
        when(save.getMapFileFromName(anyString())).thenAnswer(invocation -> new File(folder, invocation.getArgument(0) + ".dat"));
        overworld.mapStorage = spy(new MapStorage(save));
        hooks.onWorldLoad(new WorldEvent.Load(overworld));
        // Register directly: the normal Forge registration path requires LaunchClassLoader.
        Field busField = EventBus.class.getDeclaredField("busID");
        busField.setAccessible(true);
        busId = busField.getInt(MinecraftForge.EVENT_BUS);
        saveListener = event -> {
            if (event instanceof WorldEvent.Save) hooks.onWorldSave((WorldEvent.Save)event);
        };
        new WorldEvent.Save(overworld).getListenerList().register(busId, EventPriority.LOWEST, saveListener);
    }

    @After
    public void cleanup()
    {
        if (saveListener != null) ListenerList.unregisterAll(busId, saveListener);
        MoCreatures.instance = null;
        MoCreatures.proxy = null;
    }

    @Test
    public void firstWorldSavePersistsNewPetCreatedDuringChunkSerialization() throws Exception
    {
        WorldServer world = savingWorld();
        TestPet pet = new TestPet(world);
        pet.setOwnerPetId(-1);
        pet.setPosition(20, 70, -25);
        writePetDuringChunkSave(world, pet);
        world.saveAllChunks(true, null);
        MoCPetMapData loaded = reload();
        assertNotNull(loaded.getPetData("Owner"));
        assertNotNull(loaded.getPetData("Owner").getPetData(pet.getOwnerPetId()));
        assertFalse(MoCreatures.instance.mapData.isDirty());
    }

    @Test
    public void firstWorldSavePersistsLocationChangesFromChunkSerialization() throws Exception
    {
        WorldServer world = savingWorld();
        TestPet pet = new TestPet(world);
        pet.setOwnerPetId(-1);
        pet.setPosition(4, 60, 8);
        savePet(pet);
        overworld.mapStorage.saveAllData();
        pet.setPosition(20, 70, -25);
        writePetDuringChunkSave(world, pet);
        world.saveAllChunks(true, null);
        NBTTagCompound record = reload().getPetData("Owner").getPetData(pet.getOwnerPetId());
        assertEquals(20, record.getTagList("Pos", 6).func_150309_d(0), 0);
        assertEquals(-25, record.getTagList("Pos", 6).func_150309_d(2), 0);
        assertEquals(dimension, record.getInteger("Dimension"));
    }

    @Test
    public void clientAndUnrelatedStorageCannotFlushTheActiveRegistry() throws Exception
    {
        WorldServer unrelated = savingWorld();
        unrelated.mapStorage = mock(MapStorage.class);
        MoCreatures.instance.mapData.markDirty();
        clearInvocations(overworld.mapStorage);
        MinecraftForge.EVENT_BUS.post(new WorldEvent.Save(unrelated));
        WorldServer client = savingWorld();
        client.isRemote = true;
        MinecraftForge.EVENT_BUS.post(new WorldEvent.Save(client));
        verify(overworld.mapStorage, never()).saveAllData();
        assertTrue(MoCreatures.instance.mapData.isDirty());
    }

    @Test
    public void unchangedRegistryDoesNotTriggerAnExtraStorageSave() throws Exception
    {
        MoCreatures.instance.mapData.setDirty(false);
        clearInvocations(overworld.mapStorage);
        MinecraftForge.EVENT_BUS.post(new WorldEvent.Save(savingWorld()));
        verify(overworld.mapStorage, never()).saveAllData();
    }

    private WorldServer savingWorld() throws Exception
    {
        WorldServer world = mock(WorldServer.class, invocation -> {
            // WorldServerMulti skips shared map storage in saveLevel; the overworld saves it before chunks.
            if (invocation.getMethod().getName().equals("saveLevel")) {
                if (dimension == 0) overworld.mapStorage.saveAllData();
                return null;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        setProvider(world, dimension);
        world.mapStorage = overworld.mapStorage;
        ChunkProviderServer chunks = mock(ChunkProviderServer.class);
        Field field = World.class.getDeclaredField("chunkProvider");
        field.setAccessible(true);
        field.set(world, chunks);
        world.theChunkProviderServer = chunks;
        when(chunks.canSave()).thenReturn(true);
        when(chunks.func_152380_a()).thenReturn(Collections.emptyList());
        doCallRealMethod().when(world).saveAllChunks(anyBoolean(), any());
        return world;
    }

    private void writePetDuringChunkSave(WorldServer world, TestPet pet)
    {
        when(world.theChunkProviderServer.saveChunks(anyBoolean(), any())).thenAnswer(invocation -> {
            savePet(pet);
            return true;
        });
    }

    private void savePet(TestPet pet)
    {
        NBTTagCompound nbt = new NBTTagCompound();
        pet.writeToNBT(nbt);
        MoCreatures.instance.mapData.updateOwnerPet(pet, nbt);
    }

    private MoCPetMapData reload()
    {
        return (MoCPetMapData)new MapStorage(save).loadData(MoCPetMapData.class, "mocreatures");
    }

    private static void setProvider(World world, int dimension) throws Exception
    {
        WorldProvider provider = mock(WorldProvider.class);
        provider.dimensionId = dimension;
        Field field = World.class.getField("provider");
        field.setAccessible(true);
        field.set(world, provider);
    }
}

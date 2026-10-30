package drzhark.mocreatures.item;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import drzhark.mocreatures.MoCPetMapData;
import drzhark.mocreatures.MoCTools;
import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.entity.animal.MoCEntityHorse;
import net.minecraft.entity.Entity;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class AmuletTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> types() { return Arrays.asList(new Object[][] { { true }, { false } }); }
    private final boolean horse;
    private MoCItem item;
    private MoCEntityHorse pet;
    private World world;
    private EntityPlayer player;
    private IAttributeInstance maxHealth;
    private ItemStack filled;
    private Item emptyItem;
    private MoCPetMapData map;
    private AtomicInteger petId;

    public AmuletTest(boolean horse) { this.horse = horse; }

    @Before
    public void setup() throws Exception
    {
        world = mock(World.class);
        WorldProvider provider = mock(WorldProvider.class);
        Field field = World.class.getField("provider");
        field.setAccessible(true);
        field.set(world, provider);
        player = mock(EntityPlayer.class);
        player.worldObj = world;
        player.inventory = mock(InventoryPlayer.class);
        when(player.getCommandSenderName()).thenReturn("Owner");
        when(world.getPlayerEntityByName("Owner")).thenReturn(player);
        pet = mock(MoCEntityHorse.class);
        pet.worldObj = world;
        when(pet.getOwnerName()).thenReturn("Owner");
        when(pet.getName()).thenReturn("Pet");
        when(pet.getIsTamed()).thenReturn(true);
        when(pet.getType()).thenReturn(21);
        DataWatcher watcher = new DataWatcher(pet);
        watcher.addObject(6, Float.valueOf(18F));
        Field watcherField = Entity.class.getDeclaredField("dataWatcher");
        watcherField.setAccessible(true);
        watcherField.set(pet, watcher);
        petId = new AtomicInteger(-1);
        when(pet.getOwnerPetId()).thenAnswer(invocation -> petId.get());
        doAnswer(invocation -> { petId.set(invocation.getArgument(0)); return null; }).when(pet).setOwnerPetId(anyInt());
        maxHealth = mock(IAttributeInstance.class);
        when(maxHealth.getBaseValue()).thenReturn(30D);
        when(maxHealth.getAttributeValue()).thenReturn(30D);
        when(pet.getEntityAttribute(SharedMonsterAttributes.maxHealth)).thenReturn(maxHealth);
        when(world.spawnEntityInWorld(any(Entity.class))).thenReturn(true);
        MoCreatures.instance = new MoCreatures();
        map = new MoCPetMapData("test");
        MoCreatures.instance.mapData = map;
        emptyItem = new Item();
        MoCreatures.amuletGhost = emptyItem;
        MoCreatures.amuletGhostFull = new Item();
        MoCreatures.fishNet = emptyItem;
        MoCreatures.petAmulet = new Item();
        if (horse)
        {
            MoCItemHorseAmulet amulet = mock(MoCItemHorseAmulet.class, CALLS_REAL_METHODS);
            doReturn(pet).when(amulet).createHorse(world);
            doNothing().when(amulet).onReleased(any(), any());
            item = amulet;
        }
        else
        {
            MoCItemPetAmulet amulet = mock(MoCItemPetAmulet.class, CALLS_REAL_METHODS);
            doReturn(pet).when(amulet).createPet(anyString(), eq(world));
            doNothing().when(amulet).onReleased(any(), any(), anyBoolean());
            item = amulet;
        }
        filled = new ItemStack(new Item(), 1, 21);
        filled.setTagCompound(tag("Owner", 7));
    }

    @After
    public void teardown()
    {
        MoCreatures.instance = null;
        MoCreatures.amuletGhost = null;
        MoCreatures.amuletGhostFull = null;
        MoCreatures.fishNet = null;
        MoCreatures.petAmulet = null;
    }

    @Test
    public void ownerCheckUsesHeldStackEvenAfterReadingAnotherStack()
    {
        readLegacy(tag("Owner", 0));
        filled.setTagCompound(tag("SomeoneElse", 7));
        assertSame(filled, release());
        verify(world, never()).spawnEntityInWorld(any());
        verifyNoInteractions(player.inventory);
        readLegacy(tag("SomeoneElse", 0));
        filled.setTagCompound(tag("Owner", 7));
        assertSame(emptyItem, release().getItem());
        verify(world).spawnEntityInWorld(pet);
    }

    @Test
    public void ownershipStillAppliesWithoutTheRegistry()
    {
        MoCreatures.instance.mapData = null;
        filled.setTagCompound(tag("SomeoneElse", 7));
        assertSame(filled, release());
        verify(world, never()).spawnEntityInWorld(any());
    }

    @Test
    public void successfulReleaseReturnsEmptyItemAndRecordsState()
    {
        ItemStack result = release();
        assertSame(emptyItem, result.getItem());
        assertEquals(0, result.getItemDamage());
        verify(player.inventory).setInventorySlotContents(0, result);
        verify(pet).setOwner("Owner");
        verify(pet).setName("Pet");
        verify(maxHealth).setBaseValue(40);
        verify(pet).setHealth(18F);
        assertNotNull(map.getPetData("Owner").getPetData(petId.get()));
        assertFalse(map.getPetData("Owner").getInAmulet(petId.get()));
        assertTrue(map.isDirty());
    }

    @Test
    public void rejectedOrThrowingSpawnsKeepTheFilledItem()
    {
        when(world.spawnEntityInWorld(pet)).thenReturn(false);
        assertSame(filled, release());
        assertTrue(pet.isDead);
        verifyNoInteractions(player.inventory);
        assertTrue(map.getPetMap().isEmpty());
        pet.isDead = false;
        when(world.spawnEntityInWorld(pet)).thenThrow(new IllegalStateException("test rejected spawn"));
        assertSame(filled, release());
        assertTrue(pet.isDead);
        verifyNoInteractions(player.inventory);
    }

    @Test
    public void spawnListenerKillingThePetDoesNotConsumeAmulet()
    {
        when(world.spawnEntityInWorld(pet)).thenAnswer(invocation -> { pet.isDead = true; return true; });
        assertSame(filled, release());
        verifyNoInteractions(player.inventory);
    }

    @Test
    public void failedEffectsDoNotReturnTheFilledAmuletAfterSpawning()
    {
        if (horse) doThrow(new IllegalStateException("test effects")).when((MoCItemHorseAmulet)item).onReleased(any(), any());
        else doThrow(new IllegalStateException("test effects")).when((MoCItemPetAmulet)item).onReleased(any(), any(), anyBoolean());
        assertSame(emptyItem, release().getItem());
        assertFalse(pet.isDead);
        verify(world).spawnEntityInWorld(pet);
    }

    @Test
    public void clientsAndEmptyAmuletsDoNothing()
    {
        world.isRemote = true;
        assertSame(filled, release());
        world.isRemote = false;
        filled.setItemDamage(0);
        assertSame(filled, release());
        verify(world, never()).spawnEntityInWorld(any());
        verifyNoInteractions(player.inventory);
    }

    @Test
    public void missingNbtSupportsLegacyHorsesButDoesNotInventGenericPets()
    {
        filled.setTagCompound(null);
        ItemStack result = release();
        if (horse)
        {
            assertSame(emptyItem, result.getItem());
            verify(pet).setType(21);
            verify(pet).setMoCAge(100);
            verify(pet).setAdult(true);
            verify(pet).setHealth(30F);
            verify(maxHealth, never()).setBaseValue(anyDouble());
        }
        else
        {
            assertSame(filled, result);
            verify(world, never()).spawnEntityInWorld(any());
        }
    }

    @Test
    public void missingCreatureCreationKeepsTheAmulet()
    {
        if (horse) doReturn(null).when((MoCItemHorseAmulet)item).createHorse(world);
        else doReturn(null).when((MoCItemPetAmulet)item).createPet(anyString(), eq(world));
        assertSame(filled, release());
        verifyNoInteractions(player.inventory);
        verify(world, never()).spawnEntityInWorld(any());
    }

    @Test
    public void oldIdSpellingAndMissingIdAreHandledWithoutUsingZero()
    {
        NBTTagCompound nbt = tag("Owner", 7);
        nbt.removeTag("PetId");
        nbt.setInteger("PetID", 12);
        readLegacy(nbt);
        NBTTagCompound written = writeLegacy();
        assertEquals(12, written.getInteger("PetId"));
        assertFalse(written.hasKey("PetID"));
        nbt.setInteger("PetId", 9);
        readLegacy(nbt);
        assertEquals(9, writeLegacy().getInteger("PetId"));
        nbt.removeTag("PetId");
        nbt.removeTag("PetID");
        readLegacy(nbt);
        assertEquals(-1, writeLegacy().getInteger("PetId"));
        new MoCAmuletData(nbt, 21, horse).restore(pet, "Owner");
        verify(pet).setOwnerPetId(-1);
    }

    @Test
    public void oldOrInvalidHealthUsesCreatureDefaults()
    {
        NBTTagCompound nbt = tag("Owner", 7);
        nbt.removeTag("MaxHealth");
        nbt.removeTag("Health");
        new MoCAmuletData(nbt, 21, horse).restore(pet, "Owner");
        verify(maxHealth, never()).setBaseValue(anyDouble());
        verify(pet).setHealth(30F);
        for (float invalid : new float[] { 0, -5, Float.NaN, Float.POSITIVE_INFINITY })
        {
            nbt.setFloat("MaxHealth", invalid);
            nbt.setFloat("Health", invalid);
            new MoCAmuletData(nbt, 21, horse).restore(pet, "Owner");
        }
        verify(maxHealth, never()).setBaseValue(anyDouble());
        verify(pet, times(5)).setHealth(30F);
    }

    @Test
    public void ownerlessAmuletsGetANewPetIdAndSnapshotsDoNotChange()
    {
        NBTTagCompound nbt = tag("", 0);
        MoCAmuletData data = new MoCAmuletData(nbt, 21, horse);
        nbt.setString("OwnerName", "SomeoneElse");
        assertTrue(data.belongsTo("Owner"));
        data.restore(pet, "Owner");
        verify(pet).setOwnerPetId(-1);
        verify(pet).setOwner("Owner");
    }

    @Test
    public void fullInventoryDropsFilledAmuletAndSavesTheFlag()
    {
        MoCTools.recordAmuletState(pet, false);
        map.setDirty(false);
        when(player.inventory.addItemStackToInventory(any())).thenReturn(false);
        assertTrue(MoCTools.deliverFilledAmulet(pet, filled));
        verify(world).spawnEntityInWorld(argThat(entity -> entity instanceof EntityItem
                && ((EntityItem)entity).getEntityItem() == filled));
        assertTrue(map.isDirty());
        NBTTagCompound saved = new NBTTagCompound();
        map.writeToNBT(saved);
        MoCPetMapData loaded = new MoCPetMapData("test");
        loaded.readFromNBT((NBTTagCompound)saved.copy());
        assertTrue(loaded.getPetData("Owner").getInAmulet(petId.get()));
        map.setDirty(false);
        MoCTools.recordAmuletState(pet, false);
        assertTrue(map.isDirty());
        assertFalse(map.getPetData("Owner").getInAmulet(petId.get()));
    }

    @Test
    public void inventoryDeliveryDoesNotSpawnAnotherItem()
    {
        when(player.inventory.addItemStackToInventory(any())).thenReturn(true);
        assertTrue(MoCTools.deliverFilledAmulet(pet, filled));
        verify(world, never()).spawnEntityInWorld(any());
    }

    @Test
    public void fullCreativeInventoryCannotSilentlyDiscardThePet() throws Exception
    {
        PlayerCapabilities capabilities = new PlayerCapabilities();
        capabilities.isCreativeMode = true;
        Field field = EntityPlayer.class.getField("capabilities");
        field.setAccessible(true);
        field.set(player, capabilities);
        player.inventory = new InventoryPlayer(player);
        for (int i = 0; i < player.inventory.mainInventory.length; i++)
            player.inventory.mainInventory[i] = new ItemStack(emptyItem, 64, 0);
        assertTrue(MoCTools.deliverFilledAmulet(pet, filled));
        assertEquals(1, filled.stackSize);
        verify(world).spawnEntityInWorld(argThat(entity -> entity instanceof EntityItem
                && ((EntityItem)entity).getEntityItem() == filled));
    }

    @Test
    public void missingOwnerAndThrowingItemSpawnKeepCapturePending()
    {
        when(world.getPlayerEntityByName("Owner")).thenReturn(null);
        when(world.spawnEntityInWorld(any())).thenThrow(new IllegalStateException("test item spawn"));
        assertFalse(MoCTools.dropHorseAmuletWithNewPetInformation(pet));
        assertFalse(pet.isDead);
        assertFalse(map.getPetData("Owner").getInAmulet(petId.get()));
        verifyNoInteractions(player.inventory);
    }

    @Test
    public void clientFlagChangesDoNotDirtyServerRegistry()
    {
        MoCTools.recordAmuletState(pet, true);
        map.setDirty(false);
        world.isRemote = true;
        map.setInAmulet(pet, false);
        assertFalse(map.isDirty());
        assertTrue(map.getPetData("Owner").getInAmulet(petId.get()));
    }

    @Test
    public void rejectedCaptureLeavesPetEquipmentAndHeldItemAlone()
    {
        when(world.spawnEntityInWorld(any())).thenReturn(false);
        Runnable dropEquipment = mock(Runnable.class);
        assertFalse(MoCTools.captureInAmulet(pet, player, 1, dropEquipment));
        assertFalse(pet.isDead);
        verify(player.inventory, never()).setInventorySlotContents(anyInt(), any());
        verifyNoInteractions(dropEquipment);
        assertFalse(map.getPetData("Owner").getInAmulet(petId.get()));
    }

    @Test
    public void successfulCaptureRegistersTheIdBeforeDeliveringAndRemovesPet()
    {
        when(world.spawnEntityInWorld(any())).thenAnswer(invocation -> {
            EntityItem dropped = invocation.getArgument(0);
            assertFalse(pet.isDead);
            assertTrue(petId.get() >= 0);
            assertEquals(petId.get(), dropped.getEntityItem().stackTagCompound.getInteger("PetId"));
            return true;
        });
        Runnable dropEquipment = mock(Runnable.class);
        assertTrue(MoCTools.captureInAmulet(pet, player, 1, dropEquipment));
        assertTrue(pet.isDead);
        verify(player.inventory).setInventorySlotContents(0, null);
        verify(dropEquipment).run();
        assertTrue(map.getPetData("Owner").getInAmulet(petId.get()));
    }

    @Test
    public void horseDeliveryFailureCanBeRetriedWithoutLosingPet()
    {
        when(world.spawnEntityInWorld(any())).thenReturn(false);
        assertFalse(MoCTools.dropHorseAmuletWithNewPetInformation(pet));
        assertFalse(pet.isDead);
        when(world.spawnEntityInWorld(any())).thenReturn(true);
        assertTrue(MoCTools.dropHorseAmuletWithNewPetInformation(pet));
        assertTrue(map.getPetData("Owner").getInAmulet(petId.get()));
    }

    private ItemStack release() { return item.onItemRightClick(filled, world, player); }
    private void readLegacy(NBTTagCompound nbt) {
        if (horse) ((MoCItemHorseAmulet)item).readFromNBT(nbt);
        else ((MoCItemPetAmulet)item).readFromNBT(nbt);
    }
    private NBTTagCompound writeLegacy() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (horse) ((MoCItemHorseAmulet)item).writeToNBT(nbt);
        else ((MoCItemPetAmulet)item).writeToNBT(nbt);
        return nbt;
    }
    private NBTTagCompound tag(String owner, int id) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("OwnerName", owner);
        nbt.setString("Name", "Pet");
        nbt.setInteger("PetId", id);
        nbt.setInteger("CreatureType", 21);
        nbt.setFloat("MaxHealth", 40);
        nbt.setFloat("Health", 18);
        if (horse) nbt.setInteger("SpawnClass", 21);
        else nbt.setString("SpawnClass", "Horse");
        return nbt;
    }
}

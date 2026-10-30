package drzhark.mocreatures.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import drzhark.mocreatures.MoCPetData;
import drzhark.mocreatures.MoCPetMapData;
import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.entity.IMoCEntity;
import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.entity.animal.MoCEntityHorse;
import drzhark.mocreatures.network.message.MoCMessageEntityDive;
import drzhark.mocreatures.network.message.MoCMessageEntityHorseJumpKeyDown;
import drzhark.mocreatures.network.message.MoCMessageEntityHorseJumpKeyUp;
import drzhark.mocreatures.network.message.MoCMessageEntityJump;
import drzhark.mocreatures.network.message.MoCMessageUpdatePetName;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.World;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class ServerPacketHandlersTest {
    private EntityPlayerMP player;
    private World world;
    private NetworkManager network;
    private NetHandlerPlayServer handler;
    private MessageContext context;
    private Entity petEntity;
    private IMoCTameable pet;

    @Before
    public void setup() throws Exception
    {
        MoCServerPacketQueue.start();
        player = mock(EntityPlayerMP.class);
        world = mock(World.class);
        player.worldObj = world;
        when(player.getCommandSenderName()).thenReturn("Owner");
        network = mock(NetworkManager.class);
        when(network.isChannelOpen()).thenReturn(true);
        handler = new NetHandlerPlayServer(null, network, player);
        context = mock(MessageContext.class);
        when(context.getServerHandler()).thenReturn(handler);
        petEntity = mock(Entity.class, withSettings().extraInterfaces(IMoCTameable.class, IMoCEntity.class));
        pet = (IMoCTameable) petEntity;
        when(pet.getOwnerName()).thenReturn("Owner");
        when(pet.getIsTamed()).thenReturn(true);
        when(pet.getOwnerPetId()).thenReturn(7);
        when(world.getEntityByID(42)).thenReturn(petEntity);
        MoCreatures.instance = new MoCreatures();
    }

    @After
    public void teardown()
    {
        MoCServerPacketQueue.stop();
        MoCreatures.instance = null;
    }

    @Test
    public void ownerRenameIsDeferredAndUpdatesOnlyItsSavedPet()
    {
        NBTTagCompound petTag = new NBTTagCompound();
        petTag.setInteger("PetId", 7);
        petTag.setString("Name", "Before");
        NBTTagCompound otherTag = new NBTTagCompound();
        otherTag.setInteger("PetId", 8);
        otherTag.setString("Name", "Other");
        NBTTagList pets = new NBTTagList();
        pets.appendTag(petTag);
        pets.appendTag(otherTag);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("TamedList", pets);
        MoCPetMapData map = new MoCPetMapData("test");
        map.getPetMap().put("Owner", new MoCPetData(root, "Owner"));
        map.setDirty(false);
        MoCreatures.instance.mapData = map;

        rename(42);
        verify(pet, never()).setName(anyString());
        assertEquals("Before", petTag.getString("Name"));
        assertFalse(map.isDirty());
        tick();
        verify(pet).setName("New name 🐴");
        assertEquals("New name 🐴", petTag.getString("Name"));
        assertEquals("Other", otherTag.getString("Name"));
        assertTrue(map.isDirty());
    }

    @Test
    public void rejectsOtherOwnersAndUntamedOrDeadPets()
    {
        when(pet.getOwnerName()).thenReturn("SomeoneElse");
        rename(42);
        tick();
        when(pet.getOwnerName()).thenReturn("Owner");
        when(pet.getIsTamed()).thenReturn(false);
        rename(42);
        tick();
        when(pet.getIsTamed()).thenReturn(true);
        petEntity.isDead = true;
        rename(42);
        tick();
        verify(pet, never()).setName(anyString());
    }

    @Test
    public void checksOwnershipWhenExecutedAndIgnoresUnknownEntities()
    {
        rename(42);
        when(pet.getOwnerName()).thenReturn("NewOwner");
        tick();
        rename(9999);
        when(world.getEntityByID(43)).thenReturn(mock(Entity.class));
        rename(43);
        tick();
        verify(pet, never()).setName(anyString());
    }

    @Test
    public void skipsDisconnectedAndReplacedPlayers()
    {
        rename(42);
        when(network.isChannelOpen()).thenReturn(false);
        tick();
        when(network.isChannelOpen()).thenReturn(true);
        rename(42);
        handler.playerEntity = mock(EntityPlayerMP.class);
        tick();
        verify(pet, never()).setName(anyString());
    }

    @Test
    public void allowsFirstNameBeforePetDataExists()
    {
        rename(42);
        tick();
        verify(pet).setName("New name 🐴");
    }

    @Test
    public void jumpAndDiveAreDeferredAndRequireTheCurrentRider()
    {
        player.ridingEntity = petEntity;
        petEntity.riddenByEntity = player;
        IMoCEntity mount = (IMoCEntity) petEntity;
        MoCMessageEntityJump jump = new MoCMessageEntityJump();
        MoCMessageEntityDive dive = new MoCMessageEntityDive();
        jump.onMessage(jump, context);
        dive.onMessage(dive, context);
        verify(mount, never()).makeEntityJump();
        verify(mount, never()).makeEntityDive();
        tick();
        verify(mount).makeEntityJump();
        verify(mount).makeEntityDive();
        clearInvocations(mount);
        jump.onMessage(jump, context);
        dive.onMessage(dive, context);
        petEntity.riddenByEntity = null;
        tick();
        verify(mount, never()).makeEntityJump();
        verify(mount, never()).makeEntityDive();
    }

    @Test
    public void horseKeyPressAndReleaseRemainOrderedOnTheTickThread()
    {
        MoCEntityHorse horse = mock(MoCEntityHorse.class);
        player.ridingEntity = horse;
        horse.riddenByEntity = player;
        MoCMessageEntityHorseJumpKeyDown down = new MoCMessageEntityHorseJumpKeyDown();
        MoCMessageEntityHorseJumpKeyUp up = new MoCMessageEntityHorseJumpKeyUp();
        down.onMessage(down, context);
        up.onMessage(up, context);
        verify(horse, never()).setJumpKeyDown(true);
        verify(horse, never()).setJumpKeyDown(false);
        tick();
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(horse);
        order.verify(horse).setJumpKeyDown(true);
        order.verify(horse).setJumpKeyDown(false);
    }

    private void rename(int entityId)
    {
        MoCMessageUpdatePetName message = new MoCMessageUpdatePetName(entityId, "New name 🐴");
        message.onMessage(message, context);
    }

    private void tick()
    {
        new MoCServerPacketQueue().onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
    }
}

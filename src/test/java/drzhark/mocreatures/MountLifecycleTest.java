package drzhark.mocreatures;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import drzhark.mocreatures.entity.IMoCEntity;
import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.entity.MoCEntityTameableAnimal;
import drzhark.mocreatures.entity.MoCEntityTameableAquatic;
import drzhark.mocreatures.entity.MoCEntityTameableAmbient;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class MountLifecycleTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> types() {
        return Arrays.asList(new Object[][] { { MoCEntityTameableAnimal.class },
                { MoCEntityTameableAquatic.class }, { MoCEntityTameableAmbient.class } });
    }
    private final Class<? extends EntityLiving> type;
    private EntityLiving mount;
    private IMoCTameable pet;
    private IMoCEntity creature;
    private EntityPlayer player;
    private World world;
    private MoCPlayerTracker tracker;
    private MoCEventHooks hooks;

    public MountLifecycleTest(Class<? extends EntityLiving> type) { this.type = type; }

    @Before
    public void setup() throws Exception
    {
        MoCreatures.isMobConfinementLoaded = false;
        world = mock(World.class);
        player = mock(EntityPlayer.class);
        player.worldObj = world;
        mount = mock(type);
        mount.worldObj = world;
        pet = (IMoCTameable)mount;
        creature = (IMoCEntity)mount;
        when(pet.getIsTamed()).thenReturn(true);
        doCallRealMethod().when(pet).isRiderDisconnecting();
        doCallRealMethod().when(creature).riderIsDisconnecting(anyBoolean());
        doCallRealMethod().when(mount).setDead();
        DataWatcher watcher = new DataWatcher(mount);
        watcher.addObject(6, Float.valueOf(20));
        Field field = Entity.class.getDeclaredField("dataWatcher");
        field.setAccessible(true);
        field.set(mount, watcher);
        player.ridingEntity = mount;
        mount.riddenByEntity = player;
        tracker = new MoCPlayerTracker();
        hooks = new MoCEventHooks();
    }

    @After public void teardown() { MoCreatures.isMobConfinementLoaded = false; }

    @Test
    public void logoutAllowsVanillaRemovalAndWorldRejoinRestoresProtection()
    {
        mount.setDead();
        assertFalse(mount.isDead);
        tracker.onPlayerLogout(new PlayerLoggedOutEvent(player));
        assertTrue(pet.isRiderDisconnecting());
        assertFalse(mount.isDead); // Vanilla must still save it in player NBT before removing it.
        assertSame(mount, player.ridingEntity);
        mount.setDead();
        assertTrue(mount.isDead);
        mount.isDead = false;
        hooks.entityJoinWorldEvent(new EntityJoinWorldEvent(mount, world));
        assertFalse(pet.isRiderDisconnecting());
        mount.setDead();
        assertFalse(mount.isDead);
    }

    @Test
    public void disconnectFlagCanBeCleared()
    {
        creature.riderIsDisconnecting(true);
        assertTrue(pet.isRiderDisconnecting());
        creature.riderIsDisconnecting(false);
        assertFalse(pet.isRiderDisconnecting());
    }

    @Test
    public void clientLogoutAndClientJoinDoNotChangeServerMountState()
    {
        world.isRemote = true;
        tracker.onPlayerLogout(new PlayerLoggedOutEvent(player));
        assertFalse(pet.isRiderDisconnecting());
        creature.riderIsDisconnecting(true);
        hooks.entityJoinWorldEvent(new EntityJoinWorldEvent(mount, world));
        assertTrue(pet.isRiderDisconnecting());
    }

    @Test
    public void staleMountLinksCannotEnableRemoval()
    {
        mount.riddenByEntity = mock(EntityPlayer.class);
        tracker.onPlayerLogout(new PlayerLoggedOutEvent(player));
        assertFalse(pet.isRiderDisconnecting());
        mount.riddenByEntity = player;
        mount.worldObj = mock(World.class);
        tracker.onPlayerLogout(new PlayerLoggedOutEvent(player));
        assertFalse(pet.isRiderDisconnecting());
    }

    @Test
    public void deadOrMissingMountsAreIgnored()
    {
        mount.isDead = true;
        tracker.onPlayerLogout(new PlayerLoggedOutEvent(player));
        assertFalse(pet.isRiderDisconnecting());
        player.ridingEntity = null;
        tracker.onPlayerLogout(new PlayerLoggedOutEvent(player));
        player.ridingEntity = mock(Entity.class);
        tracker.onPlayerLogout(new PlayerLoggedOutEvent(player));
    }

    @Test
    public void clientRemovalAndMobConfinementStillWork()
    {
        world.isRemote = true;
        mount.setDead();
        assertTrue(mount.isDead);
        world.isRemote = false;
        mount.isDead = false;
        MoCreatures.isMobConfinementLoaded = true;
        mount.setDead();
        assertTrue(mount.isDead);
    }
}

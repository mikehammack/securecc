package com.maximarcana.securecc;

import com.maximarcana.securecc.block.BlockSecureCable;
import com.maximarcana.securecc.block.BlockSecureManipulator;
import com.maximarcana.securecc.block.BlockSecureModem;
import com.maximarcana.securecc.block.BlockSecureMonitor;
import com.maximarcana.securecc.item.ItemSecurityKey;
import com.maximarcana.securecc.manip.ManipulatorAuthData;
import com.maximarcana.securecc.tile.TileSecureCable;
import com.maximarcana.securecc.tile.TileSecureComputer;
import com.maximarcana.securecc.tile.TileSecureModem;
import com.maximarcana.securecc.tile.TileSecureMonitor;
import com.maximarcana.securecc.tile.TileSecureTurtle;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Server-side access gating. (BlockComputerBase's activation handler is
 * final, so the terminal gates for computers and turtles live here instead
 * of in the block classes. Monitors and manipulators gate in their tiles
 * and blocks directly.)
 */
@Mod.EventBusSubscriber(modid = SecureCC.MODID)
public class SecureEventHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        BlockPos pos = event.getPos();
        TileEntity te = world.getTileEntity(pos);
        EntityPlayer player = event.getEntityPlayer();

        String kind = null;
        String owner = null;

        // The Security Key opens the management GUI on a plain right-click
        // (no sneaking needed). The event is canceled so the block's normal
        // activation does not fire underneath the management screen. Other
        // blocks are untouched, so the key never blocks normal interaction.
        ItemStack held = player.getHeldItem(event.getHand());
        if (ItemSecurityKey.isSecurityKey(held)) {
            SecureAccess keyAccess = SecureAccess.forBlock(world, pos);
            if (keyAccess != null) {
                if (keyAccess.canManage(player)) {
                    SecureGuiHandler.openForBlock(player, world, pos);
                } else {
                    player.sendMessage(new TextComponentString(
                            "\u00a7cOnly the owner (or an op) can manage this block."));
                }
                event.setCanceled(true);
                return;
            }
        }

        // Shift-right-click with an empty hand opens the friend-management
        // GUI on any secure block (computers, turtles, monitors,
        // manipulators). Only the owner (or an op, when op bypass is
        // enabled) may open it. Sneaking while holding an item is vanilla's
        // "use the item, don't activate the block" gesture (e.g. placing a
        // block against the computer), so it is left alone to keep normal
        // placement working. Can be disabled via allowSneakManage to require
        // the Security Key item instead.
        if (player.isSneaking() && com.maximarcana.securecc.SecureConfig.allowSneakManage) {
            boolean handsEmpty = player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()
                    && player.getHeldItem(EnumHand.OFF_HAND).isEmpty();
            if (handsEmpty) {
                SecureAccess access = SecureAccess.forBlock(world, pos);
                if (access != null) {
                    if (access.canManage(player)) {
                        SecureGuiHandler.openForBlock(player, world, pos);
                    } else {
                        player.sendMessage(new TextComponentString(
                                "\u00a7cOnly the owner (or an op) can manage friends on this block."));
                    }
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (te instanceof TileSecureComputer) {
            TileSecureComputer tile = (TileSecureComputer) te;
            if (!tile.canUseTerminal(player)) {
                kind = "computer";
                owner = tile.getOwnerName();
            }
        } else if (te instanceof TileSecureTurtle) {
            TileSecureTurtle tile = (TileSecureTurtle) te;
            if (!tile.canUseTerminal(player)) {
                kind = "turtle";
                owner = tile.getOwnerName();
            }
        }
        if (kind != null) {
            player.sendMessage(new TextComponentString(
                    "\u00a7cThis secure " + kind + " belongs to " + owner
                            + ". You don't have access right now."));
            event.setCanceled(true);
        }
    }

    /**
     * Owner-only breaking for secure monitors, manipulators, cables, and
     * modems. Their block parents do not expose removedByPlayer, so the
     * gate lives here on the Forge break event (computers and turtles gate
     * in their own removedByPlayer overrides instead).
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        IBlockState state = event.getState();
        if (!(state.getBlock() instanceof BlockSecureMonitor)
                && !(state.getBlock() instanceof BlockSecureManipulator)
                && !(state.getBlock() instanceof BlockSecureCable)
                && !(state.getBlock() instanceof BlockSecureModem)) {
            return;
        }
        BlockPos pos = event.getPos();
        EntityPlayer player = event.getPlayer();
        SecureAccess access = null;
        if (state.getBlock() instanceof BlockSecureMonitor) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureMonitor) {
                access = ((TileSecureMonitor) te).getSecureAccess();
            }
        } else if (state.getBlock() instanceof BlockSecureCable) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureCable) {
                access = ((TileSecureCable) te).getSecureAccess();
            }
        } else if (state.getBlock() instanceof BlockSecureModem) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureModem) {
                access = ((TileSecureModem) te).getSecureAccess();
            }
        } else {
            ManipulatorAuthData data = ManipulatorAuthData.get(world);
            access = data == null ? null : data.get(pos, world);
        }
        if (access != null && !access.canBreak(player)) {
            player.sendMessage(new TextComponentString(
                    "\u00a7cThis secure block belongs to " + access.getOwnerName()
                            + ". Only the owner (or an op) can break it."));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        for (ISecureTile tile : ISecureTile.LOADED) {
            if (tile.isTileInvalid() || tile.getTileWorld() != event.player.world) continue;
            tile.getSecureAccess().revokePinSession(event.player.getUniqueID());
            if (tile.getSecureAccess().getPolicy() == Policy.SHUTDOWN
                    && tile.getSecureAccess().isOwner(event.player)) {
                tile.applyShutdownPolicy();
            }
        }
        ManipulatorAuthData data = ManipulatorAuthData.get(event.player.world);
        if (data != null) {
            for (SecureAccess access : data.all()) {
                access.revokePinSession(event.player.getUniqueID());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        for (ISecureTile tile : ISecureTile.LOADED) {
            if (tile.isTileInvalid() || tile.getTileWorld() != event.player.world) continue;
            if (tile.getSecureAccess().getPolicy() == Policy.SHUTDOWN
                    && tile.getSecureAccess().isOwner(event.player)) {
                tile.applyShutdownPolicy();
            }
        }
    }

    /**
     * Owner persistence for drops. When a secure monitor or manipulator is
     * broken (by a player, or by an explosion), stamp the block's owner NBT
     * onto the dropped item stacks so breaking and replacing the block does
     * not reset ownership. Forge fires this from dropBlockAsItemWithChance,
     * while the tile entity / manipulator auth entry still exists.
     *
     * (Computers and turtles funnel their drops through CC:Tweaked's final
     * BlockComputerBase.getDrops, which delegates to our getItem override,
     * so they stamp there instead.)
     */
    @SubscribeEvent
    public static void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        net.minecraft.block.Block block = event.getState().getBlock();
        if (!(block instanceof BlockSecureMonitor) && !(block instanceof BlockSecureManipulator)) return;
        SecureAccess access = SecureAccess.forBlock(world, event.getPos());
        if (access == null || !access.hasOwner()) return;
        for (ItemStack stack : event.getDrops()) {
            if (stack != null && !stack.isEmpty()) {
                access.stampOwnerOnto(stack);
            }
        }
    }

    /**
     * Cached reflection for Plethora's private neural tick. Resolved once at
     * class load instead of on every LivingUpdateEvent (which fires per
     * entity per tick).
     */
    private static final java.lang.reflect.Method NEURAL_ONUPDATE;
    private static final java.lang.reflect.Constructor<?> NEURAL_SLOT_CTOR;

    static {
        java.lang.reflect.Method onUpdate = null;
        java.lang.reflect.Constructor<?> slotCtor = null;
        try {
            // private static void onUpdate(ItemStack, TinySlot, EntityLivingBase, boolean)
            Class<?> neuralClass = Class.forName(
                    "org.squiddev.plethora.gameplay.neural.ItemNeuralInterface");
            Class<?> slotClass = Class.forName("org.squiddev.plethora.utils.TinySlot");
            onUpdate = neuralClass.getDeclaredMethod("onUpdate",
                    net.minecraft.item.ItemStack.class,
                    slotClass,
                    net.minecraft.entity.EntityLivingBase.class,
                    boolean.class);
            onUpdate.setAccessible(true);
            slotCtor = slotClass.getConstructor(net.minecraft.item.ItemStack.class);
        } catch (Exception e) {
            System.err.println("SecureCC: failed to resolve neural tick reflection: " + e);
        }
        NEURAL_ONUPDATE = onUpdate;
        NEURAL_SLOT_CTOR = slotCtor;
    }
    /**
     * Keep the secure neural interface's computer alive and its modules/
     * peripherals synced. Plethora's ItemNeuralInterface.onEntityLivingUpdate
     * uses NeuralHelpers.getSlot, which only recognizes Plethora's own item
     * by identity, so our subclass never gets its onUpdate called: the
     * computer times out and peripherals never attach. This handler uses our
     * worn-interface lookup and invokes Plethora's private onUpdate directly
     * (via the cached reflection above).
     */
    @SubscribeEvent
    public static void onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        net.minecraft.entity.EntityLivingBase entity = event.getEntityLiving();
        if (entity == null || entity.world == null || entity.world.isRemote) return;
        // Plethora skips players here (their item onUpdate handles it).
        if (entity instanceof net.minecraft.entity.player.EntityPlayer) return;

        ItemStack worn = com.maximarcana.securecc.item.ItemSecureNeuralInterface
                .getWornSecureInterface(entity);
        if (worn.isEmpty()) return;
        if (NEURAL_ONUPDATE == null || NEURAL_SLOT_CTOR == null) return;

        try {
            Object slot = NEURAL_SLOT_CTOR.newInstance(worn);
            // true = get (or create) the server computer, mirroring Plethora.
            NEURAL_ONUPDATE.invoke(null, worn, slot, entity, true);
        } catch (Exception e) {
            System.err.println("SecureCC: failed to tick neural computer: " + e);
        }
    }
}

package com.maximarcana.securecc;

import com.maximarcana.securecc.block.BlockSecureManipulator;
import com.maximarcana.securecc.block.BlockSecureMonitor;
import com.maximarcana.securecc.manip.ManipulatorAuthData;
import com.maximarcana.securecc.tile.TileSecureComputer;
import com.maximarcana.securecc.tile.TileSecureMonitor;
import com.maximarcana.securecc.tile.TileSecureTurtle;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
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
     * Owner-only breaking for secure monitors and manipulators. Their block
     * parents do not expose removedByPlayer, so the gate lives here on the
     * Forge break event (computers and turtles gate in their own
     * removedByPlayer overrides instead).
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        IBlockState state = event.getState();
        if (!(state.getBlock() instanceof BlockSecureMonitor)
                && !(state.getBlock() instanceof BlockSecureManipulator)) {
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
}

package com.maximarcana.securecc.tile;

import com.maximarcana.securecc.ISecureTile;
import com.maximarcana.securecc.Policy;
import com.maximarcana.securecc.SecureAccess;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * A monitor tile that tracks an owner and gates touchscreen input.
 * Rendering and multi-block merging are inherited untouched; only the
 * player-triggered touch event requires authorisation.
 */
public class TileSecureMonitor extends TileMonitor implements ISecureTile {
    private final SecureAccess access = new SecureAccess();

    public TileSecureMonitor() {
        access.setDirtyMark(this::markDirty);
    }

    @Override
    public boolean isTileInvalid() {
        return super.isInvalid();
    }

    @Override
    public net.minecraft.world.World getTileWorld() {
        return super.getWorld();
    }

    @Override
    public SecureAccess getSecureAccess() {
        return access;
    }

    @Override
    public void validate() {
        super.validate();
        ISecureTile.LOADED.add(this);
    }

    @Override
    public void invalidate() {
        ISecureTile.LOADED.remove(this);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        ISecureTile.LOADED.remove(this);
        super.onChunkUnload();
    }

    // ---------- NBT ----------

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        access.readFromNBT(tag);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        access.writeToNBT(tag);
        return tag;
    }

    // ---------- Touch gate ----------

    /**
     * Intercepts player clicks before the inherited touch event fires.
     * Only approved players may generate touchscreen input; everyone can
     * still see the screen.
     */
    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, EnumFacing facing,
                             float hitX, float hitY, float hitZ) {
        World world = getWorld();
        if (world != null && !world.isRemote && !access.canUseTerminal(player, world)) {
            player.sendMessage(new TextComponentString(
                    "\u00a7cThis secure monitor belongs to " + access.getOwnerName()
                            + ". You are not authorised to touch it."));
            return true;
        }
        return super.onActivate(player, hand, facing, hitX, hitY, hitZ);
    }

    // ---------- Delegates (same API as TileSecureComputer) ----------

    public boolean hasOwner() {
        return access.hasOwner();
    }

    public String getOwnerName() {
        return access.getOwnerName();
    }

    public void setOwner(EntityPlayer player) {
        access.setOwner(player);
    }

    public boolean isOwner(EntityPlayer player) {
        return access.isOwner(player);
    }

    public boolean isFriend(EntityPlayer player) {
        return access.isFriend(player);
    }

    public void addFriend(UUID id, String name) {
        access.addFriend(id, name);
    }

    public void removeFriend(UUID id) {
        access.removeFriend(id);
    }

    public String getFriendNames() {
        return access.getFriendNames();
    }

    public String getPin() {
        return access.getPin();
    }

    public void setPin(String pin) {
        access.setPin(pin);
    }

    public void grantPinSession(UUID playerId) {
        access.grantPinSession(playerId);
    }

    public void revokePinSession(UUID playerId) {
        access.revokePinSession(playerId);
    }

    public Policy getPolicy() {
        return access.getPolicy();
    }

    public void setPolicy(Policy policy) {
        access.setPolicy(policy);
    }

    /** Can this player break the block? Owner or op only. */
    public boolean canBreak(EntityPlayer player) {
        return access.canBreak(player);
    }

    /** Can this player change policy/PIN/friends? Owner or op only. */
    public boolean canManage(EntityPlayer player) {
        return access.canManage(player);
    }

    /** Monitors have no computer; no shutdown behaviour. */
    @Override
    public void applyShutdownPolicy() {
    }
}

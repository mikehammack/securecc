package com.maximarcana.securecc.tile;

import com.maximarcana.securecc.ISecureTile;
import com.maximarcana.securecc.Policy;
import com.maximarcana.securecc.SecureAccess;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * A computer tile that tracks an owner and enforces access control.
 * All behaviour beyond storage lives in the block (placement/breaking),
 * the event handler (GUI access, offline policies) and the command.
 */
public class TileSecureComputer extends TileComputer implements ISecureTile {
    private final SecureAccess access = new SecureAccess();

    public TileSecureComputer() {
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

    // ---------- Ownership (delegates) ----------

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

    public static boolean isOp(EntityPlayer player) {
        return SecureAccess.isOp(player);
    }

    public boolean isOwnerOnline() {
        return access.isOwnerOnline(world);
    }

    // ---------- Friends / PIN (delegates) ----------

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

    // ---------- Policy (delegates) ----------

    public Policy getPolicy() {
        return access.getPolicy();
    }

    public void setPolicy(Policy policy) {
        access.setPolicy(policy);
    }

    /** Can this player open and use the terminal right now? */
    public boolean canUseTerminal(EntityPlayer player) {
        return access.canUseTerminal(player, world);
    }

    /** Can this player break the block? Owner or op only. */
    public boolean canBreak(EntityPlayer player) {
        return access.canBreak(player);
    }

    /** Can this player change policy/PIN/friends? Owner or op only. */
    public boolean canManage(EntityPlayer player) {
        return access.canManage(player);
    }

    /** Enforce the SHUTDOWN offline policy. Called on owner login/logout. */
    @Override
    public void applyShutdownPolicy() {
        if (access.getPolicy() != Policy.SHUTDOWN || world == null || world.isRemote) return;
        ServerComputer computer = getServerComputer();
        if (computer == null) return;
        if (access.isOwnerOnline(world)) {
            if (!computer.isOn()) computer.turnOn();
        } else {
            if (computer.isOn()) computer.shutdown();
        }
    }
}

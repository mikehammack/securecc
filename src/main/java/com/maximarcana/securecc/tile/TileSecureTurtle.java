package com.maximarcana.securecc.tile;

import com.maximarcana.securecc.ISecureTile;
import com.maximarcana.securecc.Policy;
import com.maximarcana.securecc.SecureAccess;
import com.maximarcana.securecc.SecurePeripheralGate;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.UUID;

/**
 * A turtle tile that tracks an owner and enforces access control.
 * The owner/friends/policy state rides on the tile, so it survives the
 * turtle moving around the world.
 */
public class TileSecureTurtle extends TileTurtle implements ISecureTile {
    private final SecureAccess access = new SecureAccess();

    public TileSecureTurtle() {
        super(ComputerFamily.Advanced);
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

    // ---------- Peripheral confinement ----------

    @Override
    public IPeripheral getPeripheral(EnumFacing side) {
        // A vanilla computer/turtle next door must not wrap this turtle
        // (turnOn/shutdown/reboot/getID/getLabel/...). Only secure hardware
        // on the queried side gets the "turtle" peripheral.
        return SecurePeripheralGate.gate(this, side, super.getPeripheral(side));
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

    public boolean isOwnerOnline() {
        return access.isOwnerOnline(world);
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

    public boolean hasPin() {
        return access.hasPin();
    }

    public boolean checkPin(String pin) {
        return access.checkPin(pin);
    }

    public void setPin(String pin) {
        access.setPin(pin);
    }

    public boolean isPinLockedOut() {
        return access.isPinLockedOut();
    }

    public long getPinLockoutRemainingSeconds() {
        return access.getPinLockoutRemainingSeconds();
    }

    public void recordPinAttempt(boolean success) {
        access.recordPinAttempt(success);
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

    /** Can this player open and use the turtle's terminal right now? */
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

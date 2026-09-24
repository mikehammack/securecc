package com.maximarcana.securecc.tile;

import com.maximarcana.securecc.ISecureTile;
import com.maximarcana.securecc.SecureAccess;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * The secure networking cable tile.
 *
 * Cables are pure wire: they carry no peripheral and talk to no computers
 * directly. They exist so {@link com.maximarcana.securecc.net.SecureWiredNetwork}
 * can flood encrypted packets between secure modems linked by cable.
 * Ownership is still tracked for break protection.
 */
public class TileSecureCable extends TileEntity implements ISecureTile {
    private final SecureAccess access = new SecureAccess();

    public TileSecureCable() {
        access.setDirtyMark(this::markDirty);
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
    public void applyShutdownPolicy() {
        // No computer to shut down; no-op.
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

    @Override
    public boolean isTileInvalid() {
        return isInvalid();
    }

    @Override
    public net.minecraft.world.World getTileWorld() {
        return getWorld();
    }
}

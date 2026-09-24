package com.maximarcana.securecc.tile;

import com.maximarcana.securecc.ISecureTile;
import com.maximarcana.securecc.SecureAccess;
import com.maximarcana.securecc.SecureConfig;
import com.maximarcana.securecc.net.SecureCrypto;
import com.maximarcana.securecc.net.SecureModemNetwork;
import com.maximarcana.securecc.net.SecureWiredNetwork;
import com.maximarcana.securecc.peripheral.SecureModemPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The secure modem.
 *
 * Provides the "secure_modem" peripheral to adjacent computers. Transmits
 * AES-encrypted packets (owner-derived key) in two ways:
 * <ul>
 *   <li>Wireless: to all same-owner secure modems in range via
 *       {@link SecureModemNetwork} — a private network, completely separate
 *       from ComputerCraft's wireless network.</li>
 *   <li>Wired: through adjacent secure cables to other modems linked by
 *       cable via {@link SecureWiredNetwork}.</li>
 * </ul>
 * Vanilla modems cannot see, read, or inject traffic on either path.
 * The cable is pure wire; the modem is the peripheral.
 */
public class TileSecureModem extends TileEntity implements ISecureTile {
    private final SecureAccess access = new SecureAccess();
    /** One peripheral per attached side (a modem can serve several computers). */
    private final Map<EnumFacing, SecureModemPeripheral> peripherals =
            new HashMap<EnumFacing, SecureModemPeripheral>();

    public TileSecureModem() {
        access.setDirtyMark(this::markDirty);
    }

    @Override
    public SecureAccess getSecureAccess() {
        return access;
    }

    // ---------- Network registration ----------

    @Override
    public void validate() {
        super.validate();
        ISecureTile.LOADED.add(this);
        if (getWorld() != null && !getWorld().isRemote) SecureModemNetwork.add(this);
    }

    @Override
    public void invalidate() {
        ISecureTile.LOADED.remove(this);
        if (getWorld() != null && !getWorld().isRemote) SecureModemNetwork.remove(this);
        super.invalidate();
    }

    @Override
    public void applyShutdownPolicy() {
        // No computer to shut down; no-op.
    }

    // ---------- Peripheral ----------

    /** Called by the block's IPeripheralProvider. */
    public IPeripheral getPeripheral(EnumFacing side) {
        SecureModemPeripheral p = peripherals.get(side);
        if (p == null) {
            p = new SecureModemPeripheral(new SecureModemPeripheral.Transport() {
                @Override
                public void transmit(int channel, int replyChannel, String plaintext) {
                    transmitMessage(channel, replyChannel, plaintext);
                }

                @Override
                public boolean isWireless() {
                    return true;
                }

                @Override
                public Map<String, IPeripheral> scanRemotePeripherals() {
                    if (getWorld() == null || getWorld().isRemote) {
                        return Collections.<String, IPeripheral>emptyMap();
                    }
                    return SecureWiredNetwork.findRemotePeripherals(
                            getWorld(), getPos());
                }
            });
            peripherals.put(side, p);
        }
        return p;
    }

    private void transmitMessage(int channel, int replyChannel, String plaintext) {
        if (getWorld() == null || getWorld().isRemote) return;
        UUID owner = access.getOwnerId();
        if (owner == null) return; // unowned modems stay silent
        // Encrypt once: both paths carry the same ciphertext, so one send
        // costs a single AES operation instead of two.
        String encrypted = SecureCrypto.encrypt(owner, replyChannel, plaintext);
        if (encrypted == null) return;
        // Wired path first. Modems reached by cable are excluded from the
        // wireless broadcast below, so one send delivers exactly once even
        // when a wired peer is also in wireless range. The adjacency check
        // is O(6); the BFS only runs when cables are actually attached, and
        // its result is reused for both delivery and exclusion.
        Set<TileSecureModem> wired = hasAdjacentCable()
                ? SecureWiredNetwork.findConnectedModems(getWorld(), getPos())
                : Collections.<TileSecureModem>emptySet();
        SecureWiredNetwork.deliverWired(channel, encrypted, wired);
        SecureModemNetwork.transmit(this, channel, encrypted,
                SecureConfig.modemRange, wired);
    }

    /** True when at least one secure cable touches this modem. O(6). */
    private boolean hasAdjacentCable() {
        for (EnumFacing facing : EnumFacing.values()) {
            TileEntity te = getWorld().getTileEntity(getPos().offset(facing));
            if (te instanceof TileSecureCable && !((TileSecureCable) te).isInvalid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called by {@link SecureModemNetwork} when an encrypted wireless packet arrives.
     * Decrypts with the owner key; failures (different owner) are dropped
     * silently. Must run on the server thread.
     */
    public void receive(int channel, String encrypted, double distance) {
        deliver(channel, encrypted, distance);
    }

    /**
     * Called by {@link SecureWiredNetwork} when an encrypted wired packet arrives.
     * Distance is 0 for wired. Must run on the server thread.
     */
    public void receiveWired(int channel, String encrypted) {
        deliver(channel, encrypted, 0.0D);
    }

    private void deliver(int channel, String encrypted, double distance) {
        if (getWorld() == null || getWorld().isRemote) return;
        UUID owner = access.getOwnerId();
        if (owner == null) return;
        String[] decrypted = SecureCrypto.decrypt(owner, encrypted);
        if (decrypted == null) return; // not ours — drop
        int replyChannel;
        try {
            replyChannel = Integer.parseInt(decrypted[0]);
        } catch (NumberFormatException e) {
            return;
        }
        String message = decrypted[1];
        for (SecureModemPeripheral p : peripherals.values()) {
            p.receiveMessage(channel, replyChannel, message, distance);
        }
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

package com.maximarcana.securecc.net;

import com.maximarcana.securecc.tile.TileSecureModem;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The secure wireless modem network.
 *
 * A private registry (dimension -> modem tiles) completely separate from
 * ComputerCraft's wireless network. Transmission is delivered only to
 * modems owned by the same UUID, and every payload is AES-encrypted with
 * the owner-derived key (see {@link SecureCrypto}), so even a
 * same-channel listener owned by someone else receives nothing usable.
 *
 * Tiles register on validate() and unregister on invalidate(); all calls
 * must happen on the server thread.
 */
public final class SecureModemNetwork {
    private SecureModemNetwork() {
    }

    /** Dimension ID -> live secure modem tiles. */
    private static final Map<Integer, Set<TileSecureModem>> MODEMS = new HashMap<Integer, Set<TileSecureModem>>();

    public static void add(TileSecureModem modem) {
        if (modem == null || modem.getWorld() == null) return;
        int dim = modem.getWorld().provider.getDimensionType().getId();
        Set<TileSecureModem> set = MODEMS.get(dim);
        if (set == null) {
            set = new HashSet<TileSecureModem>();
            MODEMS.put(dim, set);
        }
        set.add(modem);
    }

    public static void remove(TileSecureModem modem) {
        if (modem == null || modem.getWorld() == null) return;
        int dim = modem.getWorld().provider.getDimensionType().getId();
        Set<TileSecureModem> set = MODEMS.get(dim);
        if (set != null) {
            set.remove(modem);
            if (set.isEmpty()) MODEMS.remove(dim);
        }
    }

    /**
     * Transmit an encrypted message to every same-owner modem in range.
     * Modems in {@code exclude} (already reached via cable) are skipped so
     * one send delivers exactly once; the check runs before the distance
     * math. The payload must already be encrypted with the sender's owner
     * key; receivers whose owner differs fail to decrypt and drop it.
     */
    public static void transmit(TileSecureModem sender, int channel,
                               String encrypted, double range,
                               Set<TileSecureModem> exclude) {
        if (sender == null || sender.getWorld() == null) return;
        if (sender.getSecureAccess().getOwnerId() == null) return; // unowned modems stay silent

        int dim = sender.getWorld().provider.getDimensionType().getId();
        Set<TileSecureModem> set = MODEMS.get(dim);
        if (set == null) return;
        BlockPos from = sender.getPos();
        double rangeSq = range * range;

        // Copy to avoid concurrent modification if a receiver unloads mid-loop.
        for (TileSecureModem modem : new HashSet<TileSecureModem>(set)) {
            if (modem == sender || modem.isInvalid()) continue;
            // Already got this message over the wire: skip it entirely.
            if (exclude != null && exclude.contains(modem)) continue;
            if (modem.getWorld() == null) continue;
            // Skip modems in unloaded chunks (they re-register on validate).
            if (!modem.getWorld().isBlockLoaded(modem.getPos())) continue;
            double distSq = modem.getPos().distanceSq(from);
            if (distSq > rangeSq) continue;
            modem.receive(channel, encrypted, Math.sqrt(distSq));
        }
    }
}

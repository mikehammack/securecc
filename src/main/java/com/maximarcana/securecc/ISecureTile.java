package com.maximarcana.securecc;

import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implemented by secure tile entities (computer, turtle, monitor).
 * The Plethora manipulator is not a tile of ours, so it is handled
 * separately via {@link com.maximarcana.securecc.manip.ManipulatorAuthData}.
 */
public interface ISecureTile {
    SecureAccess getSecureAccess();

    /**
     * Named to avoid colliding with TileEntity.getWorld(): SpecialSource
     * renames overrides of vanilla methods to SRG, which would break the
     * interface dispatch (AbstractMethodError).
     */
    World getTileWorld();

    /**
     * Named to avoid colliding with TileEntity.isInvalid(): SpecialSource
     * renames overrides of vanilla methods to SRG, which would break the
     * interface dispatch (AbstractMethodError).
     */
    boolean isTileInvalid();

    /** Enforce the SHUTDOWN offline policy (no-op when not applicable). */
    void applyShutdownPolicy();

    /** Every loaded secure tile, for login/logout policy handling. */
    Set<ISecureTile> LOADED =
            Collections.newSetFromMap(new ConcurrentHashMap<ISecureTile, Boolean>());
}

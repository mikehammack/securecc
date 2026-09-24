package com.maximarcana.securecc;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/**
 * Peripheral confinement gate for secure tiles.
 *
 * Secure tiles (computer, turtle, monitor) expose themselves as ComputerCraft
 * peripherals to adjacent computers through {@code IPeripheralTile}. Without
 * a gate, any vanilla computer or turtle placed next door could wrap them
 * and call turnOn/shutdown/reboot/getID/getLabel/..., or drive a secure
 * monitor. The gate only serves the peripheral when the tile on the queried
 * side is one of our own secure tiles.
 *
 * CC gives the provider no computer context, so the check is on hardware
 * type, not on the acting player: ownership and the security manager stay
 * the trust anchor for who may place and configure secure hardware.
 */
public final class SecurePeripheralGate {
    private SecurePeripheralGate() {}

    /**
     * @param self     the secure tile being wrapped
     * @param side     the side of {@code self} facing the requesting computer
     *                 (CC's scan convention: {@code self.pos.offset(side)} is
     *                 the tile asking for the peripheral)
     * @param upstream the peripheral the superclass would serve
     * @return the peripheral when the requester is a secure tile, else null
     *         (null makes {@code peripheral.wrap} return nil and hides the
     *         tile from {@code peripheral.find})
     */
    public static IPeripheral gate(TileEntity self, EnumFacing side,
                                  IPeripheral upstream) {
        if (upstream == null || self.getWorld() == null) return null;
        TileEntity neighbor =
                self.getWorld().getTileEntity(self.getPos().offset(side));
        return (neighbor instanceof ISecureTile) ? upstream : null;
    }
}

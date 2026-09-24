package com.maximarcana.securecc.neural;

import com.maximarcana.securecc.item.ItemSecureNeuralInterface;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import org.squiddev.plethora.gameplay.neural.ContainerNeuralInterface;

/**
 * Plethora's neural interface container with the interact check fixed for
 * the secure subclass.
 *
 * <p>Plethora's {@code canInteractWith} compares its stack by reference
 * against {@code NeuralHelpers.getStack(parent)}, which only recognizes
 * Plethora's own registered item by identity. It always fails for the
 * secure neural interface, so the server closed the GUI a tick after
 * opening it (the screen flashed up and vanished). This version compares
 * against SecureCC's own worn-interface lookup instead.</p>
 */
public class SecureNeuralContainer extends ContainerNeuralInterface {
    private final EntityLivingBase target;
    private final ItemStack worn;

    public SecureNeuralContainer(IInventory playerInventory, EntityLivingBase target,
                                 ItemStack worn) {
        super(playerInventory, target, worn);
        this.target = target;
        this.worn = worn;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (player == null || !player.isEntityAlive() || !target.isEntityAlive()) {
            return false;
        }
        // The same stack instance must still be worn by the target.
        return worn == ItemSecureNeuralInterface.getWornSecureInterface(target);
    }
}

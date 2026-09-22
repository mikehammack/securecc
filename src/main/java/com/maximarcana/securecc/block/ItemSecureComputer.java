package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureCC;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.IComputerItem;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Item form of the secure computer. Implements IComputerItem so CC's
 * placement logic restores the computer ID and label from the stack's NBT.
 *
 * Note: the creative tab is assigned in SecureCC.init, not here — CC creates
 * its tab in preInit, which runs after item registration.
 */
public class ItemSecureComputer extends ItemBlock implements IComputerItem {
    public ItemSecureComputer(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setTranslationKey(SecureCC.MODID + ".secure_computer");
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "Secure Computer";
    }

    @Override
    public ComputerFamily getFamily(ItemStack stack) {
        return ComputerFamily.Advanced;
    }

    @Override
    public ItemStack withFamily(ItemStack stack, ComputerFamily family) {
        return stack;
    }
}

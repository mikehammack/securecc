package com.maximarcana.securecc.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/** Item form of the secure monitor. */
public class ItemSecureMonitor extends ItemBlock {
    public ItemSecureMonitor(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setTranslationKey(block.getTranslationKey());
    }

    @Override
    public String getItemStackDisplayName(net.minecraft.item.ItemStack stack) {
        return "Secure Monitor";
    }
}

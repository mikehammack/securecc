package com.maximarcana.securecc.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/** Item form of the secure manipulator (always Mark II grade). */
public class ItemSecureManipulator extends ItemBlock {
    public ItemSecureManipulator(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setTranslationKey(block.getTranslationKey());
    }

    @Override
    public String getItemStackDisplayName(net.minecraft.item.ItemStack stack) {
        return "Secure Manipulator";
    }
}

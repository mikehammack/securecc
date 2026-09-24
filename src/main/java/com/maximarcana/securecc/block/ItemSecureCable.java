package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureCC;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/** Item form of the secure networking cable. */
public class ItemSecureCable extends ItemBlock {
    public ItemSecureCable(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setTranslationKey(SecureCC.MODID + ".secure_cable");
    }

    @Override
    public String getItemStackDisplayName(net.minecraft.item.ItemStack stack) {
        return "Secure Networking Cable";
    }
}

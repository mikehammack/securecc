package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureCC;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/** Item form of the secure wireless modem. */
public class ItemSecureModem extends ItemBlock {
    public ItemSecureModem(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setTranslationKey(SecureCC.MODID + ".secure_modem");
    }

    @Override
    public String getItemStackDisplayName(net.minecraft.item.ItemStack stack) {
        return "Secure Modem";
    }
}

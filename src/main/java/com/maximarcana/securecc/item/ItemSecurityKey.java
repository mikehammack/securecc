package com.maximarcana.securecc.item;

import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureGuiHandler;
import dan200.computercraft.ComputerCraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;

/**
 * Handheld management tool. Right-clicking a secure block (computer, turtle,
 * monitor, manipulator) while holding the key opens the management GUI
 * (friends / PIN / policy) for that block — handled in
 * {@link com.maximarcana.securecc.SecureEventHandler}, which suppresses the
 * block's normal activation. Right-clicking a mob (or player) wearing a
 * secure neural interface opens the GUI for that interface. Anything else
 * is left untouched.
 *
 * <p>Opening always requires {@code canManage} on the server; unauthorized
 * players get the usual denial instead of the screen.</p>
 */
public class ItemSecurityKey extends Item {
    public ItemSecurityKey() {
        setRegistryName(SecureCC.MODID, "security_key");
        setTranslationKey(SecureCC.MODID + ".security_key");
        setMaxStackSize(1);
        // Assigned here (not in SecureCC.init) so JEI and the creative
        // inventory see the item: late tab assignment happens after JEI
        // builds its ingredient list, which also hid this item's recipe.
        setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    /**
     * Hardcoded display name, matching the convention of the secure block
     * items (which also override this instead of relying on the lang file).
     */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "Security Key";
    }

    /** True when this stack is a Security Key. */
    public static boolean isSecurityKey(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemSecurityKey;
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player,
                                           EntityLivingBase target, EnumHand hand) {
        ItemStack worn = ItemSecureNeuralInterface.getWornSecureInterface(target);
        if (worn.isEmpty()) return false;
        if (player.getEntityWorld().isRemote) return true;
        ItemSecureNeuralInterface item = (ItemSecureNeuralInterface) worn.getItem();
        if (item.getAccess(worn).canManage(player)) {
            SecureGuiHandler.openForEntity(player, player.getEntityWorld(),
                    target.getEntityId());
        } else {
            player.sendMessage(new TextComponentString(
                    "\u00a7cOnly the owner (or an op) can manage this neural interface."));
        }
        return true;
    }
}

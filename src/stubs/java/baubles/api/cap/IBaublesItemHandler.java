package baubles.api.cap;

import net.minecraft.item.ItemStack;

/**
 * Compile-only stub for the Baubles item handler capability. The real
 * interface comes from the Baubles mod at runtime; this is never packaged.
 * Only the methods SecureCC calls are declared.
 */
public interface IBaublesItemHandler {
    int getSlots();

    ItemStack getStackInSlot(int slot);
}

package com.maximarcana.securecc.recipe;

import com.maximarcana.securecc.ModItems;
import com.maximarcana.securecc.item.ItemSecureNeuralInterface;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.IComputerItem;
import dan200.computercraft.shared.peripheral.common.IPeripheralItem;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.turtle.items.ITurtleItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.squiddev.plethora.gameplay.modules.ManipulatorType;

import java.util.function.Predicate;

/**
 * Handles NBT preservation for SecureCC upgrade recipes.
 * The recipes themselves are standard minecraft:crafting_shaped JSONs (which
 * always load reliably). When one crafts, this copies the source device's NBT
 * (computer ID, label, turtle upgrades/fuel, etc.) onto the secure output.
 */
public class CraftingHandler {

    private enum Kind {
        COMPUTER(ModItems.SECURE_COMPUTER,
                stack -> stack.getItem() instanceof IComputerItem
                        && !(stack.getItem() instanceof ITurtleItem)
                        && ((IComputerItem) stack.getItem()).getFamily(stack) == ComputerFamily.Advanced),
        TURTLE(ModItems.SECURE_TURTLE,
                stack -> stack.getItem() instanceof ITurtleItem
                        && ((IComputerItem) stack.getItem()).getFamily(stack) == ComputerFamily.Advanced),
        MONITOR(ModItems.SECURE_MONITOR,
                stack -> stack.getItem() instanceof IPeripheralItem
                        && ((IPeripheralItem) stack.getItem()).getPeripheralType(stack) == PeripheralType.AdvancedMonitor),
        MANIPULATOR(ModItems.SECURE_MANIPULATOR,
                CraftingHandler::isMark2Manipulator),
        NEURAL(ModItems.SECURE_NEURAL_INTERFACE,
                stack -> new ResourceLocation("plethora", "neuralinterface")
                        .equals(stack.getItem().getRegistryName()));

        final Item output;
        final Predicate<ItemStack> matcher;

        Kind(Item output, Predicate<ItemStack> matcher) {
            this.output = output;
            this.matcher = matcher;
        }
    }

    private static boolean isMark2Manipulator(ItemStack stack) {
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null || !new ResourceLocation("plethora", "manipulator").equals(id)) return false;
        return stack.getMetadata() == ManipulatorType.MARK_2.ordinal();
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack output = event.crafting;
        if (output.isEmpty()) return;

        // Find which kind of secure device was crafted (if any)
        Kind kind = null;
        for (Kind k : Kind.values()) {
            if (output.getItem() == k.output) {
                kind = k;
                break;
            }
        }
        if (kind == null) return;

        // Find the source device in the crafting matrix and copy its NBT
        IInventory matrix = event.craftMatrix;
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            ItemStack stack = matrix.getStackInSlot(i);
            if (!stack.isEmpty() && kind.matcher.test(stack)) {
                if (stack.hasTagCompound()) {
                    output.setTagCompound(stack.getTagCompound().copy());
                }
                // Copy the item handler capability (neural interface modules,
                // etc.) — setTagCompound does not copy capabilities.
                copyItemHandler(stack, output);
                break;
            }
        }

        // The neural interface has no placement step, so the crafter becomes
        // its owner right here; blocks are claimed on placement instead.
        if (kind == Kind.NEURAL && event.player != null) {
            ((ItemSecureNeuralInterface) output.getItem()).claim(output, event.player);
        }
    }

    /**
     * Copy the item handler capability (module inventory) from source to
     * destination. The main NBT tag does not include capability data.
     * Uses reflection because ItemStack.getCapability is a Forge-added
     * method not present in the compile-time MCP jars.
     */
    private static void copyItemHandler(ItemStack source, ItemStack dest) {
        try {
            java.lang.reflect.Method getCap = ItemStack.class.getMethod(
                    "getCapability",
                    net.minecraftforge.common.capabilities.Capability.class,
                    net.minecraft.util.EnumFacing.class);
            IItemHandler srcHandler = (IItemHandler) getCap.invoke(source,
                    CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            IItemHandler dstHandler = (IItemHandler) getCap.invoke(dest,
                    CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (srcHandler == null || dstHandler == null) return;
            int slots = Math.min(srcHandler.getSlots(), dstHandler.getSlots());
            for (int i = 0; i < slots; i++) {
                ItemStack module = srcHandler.getStackInSlot(i);
                if (!module.isEmpty()) {
                    dstHandler.insertItem(i, module.copy(), false);
                }
            }
        } catch (Exception e) {
            // If reflection fails, modules just won't copy; NBT still did.
            System.err.println("SecureCC: failed to copy item handler: " + e);
        }
    }
}

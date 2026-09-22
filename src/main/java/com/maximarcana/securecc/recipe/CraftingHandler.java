package com.maximarcana.securecc.recipe;

import com.maximarcana.securecc.ModItems;
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
                CraftingHandler::isMark2Manipulator);

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
                break;
            }
        }
    }
}

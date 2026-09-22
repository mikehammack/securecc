package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureCC;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.TurtleUpgrades;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.turtle.items.ItemTurtleBase;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Item form of the secure turtle. Reports the advanced family. */
public class ItemSecureTurtle extends ItemTurtleBase {
    public ItemSecureTurtle(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setTranslationKey(SecureCC.MODID + ".secure_turtle");
    }

    @Override
    public String getItemStackDisplayName(net.minecraft.item.ItemStack stack) {
        return "Secure Turtle";
    }

    @Override
    public ComputerFamily getFamily() {
        return ComputerFamily.Advanced;
    }

    @Override
    public ResourceLocation getOverlay(ItemStack stack) {
        return null;
    }

    /**
     * Turtle upgrades for the item's tooltip/model. Mirrors vanilla
     * ItemTurtleNormal: resolved from the stack's NBT (the upgrade recipe
     * copies NBT, so upgrades survive the upgrade).
     */
    @Override
    public ITurtleUpgrade getUpgrade(ItemStack stack, TurtleSide side) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) return null;
        String key = side == TurtleSide.Left ? "leftUpgrade" : "rightUpgrade";
        if (!nbt.hasKey(key)) return null;
        ITurtleUpgrade upgrade = TurtleUpgrades.get(nbt.getString(key));
        return upgrade != null ? upgrade : TurtleUpgrades.get(nbt.getShort(key));
    }

    /**
     * Fuel level for the item's durability bar. Mirrors vanilla
     * ItemTurtleNormal: read from the stack's NBT (the upgrade recipe
     * copies NBT, so fuel survives the upgrade).
     */
    @Override
    public int getFuelLevel(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("fuelLevel")) {
            return stack.getTagCompound().getInteger("fuelLevel");
        }
        return 0;
    }

    /**
     * ItemTurtleBase builds the unlocalized name from the computer family
     * (an Advanced-family turtle reports as "Advanced Turtle"); force the
     * secure name so the item reads as "Secure Turtle".
     * (func_77667_c is the SRG name visible on this toolchain.)
     */
    @Override
    public String func_77667_c(ItemStack stack) {
        return "tile." + SecureCC.MODID + ".secure_turtle";
    }
}

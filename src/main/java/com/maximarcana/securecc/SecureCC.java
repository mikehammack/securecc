package com.maximarcana.securecc;

import com.maximarcana.securecc.block.BlockSecureManipulator;
import com.maximarcana.securecc.command.CommandSecureCC;
import com.maximarcana.securecc.recipe.CraftingHandler;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

@Mod(modid = SecureCC.MODID, name = SecureCC.NAME, version = SecureCC.VERSION,
        dependencies = "required-after:computercraft@[1.89.2,);required-after:plethora@[1.2.3,)")
public class SecureCC {
    public static final String MODID = "securecc";
    public static final String NAME = "Secure CC";
    public static final String VERSION = "1.0.0";

    public SecureCC() {
        // Load config in the constructor (before any blocks are created) so
        // block hardness and enable-flags are available at construction time.
        SecureConfig.init(new java.io.File(
                net.minecraftforge.fml.common.Loader.instance().getConfigDir(),
                MODID + ".cfg"));
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Register the crafting handler that preserves NBT (computer ID,
        // label, turtle upgrades/fuel, etc.) when upgrading to secure tier.
        // The recipes themselves are standard minecraft:crafting_shaped JSONs.
        MinecraftForge.EVENT_BUS.register(new CraftingHandler());
        // Register the secure manipulator as a CC peripheral provider so
        // adjoining computers can wrap it. Must be in preInit, before CC
        // queries providers. Delegates to BlockSecureManipulator's peripheral
        // logic (which builds the IPeripheral from the TileManipulator).
        if (SecureConfig.enableManipulator) {
            ComputerCraftAPI.registerPeripheralProvider(new IPeripheralProvider() {
                @Override
                public IPeripheral getPeripheral(World world, BlockPos pos, EnumFacing side) {
                    if (world.getBlockState(pos).getBlock() != ModBlocks.SECURE_MANIPULATOR) return null;
                    // Use the block's getPeripheral which delegates to Plethora's logic
                    if (ModBlocks.SECURE_MANIPULATOR instanceof IPeripheralProvider) {
                        return ((IPeripheralProvider) ModBlocks.SECURE_MANIPULATOR).getPeripheral(world, pos, side);
                    }
                    return null;
                }
            });
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // CC creates its creative tab in preInit (after item registration),
        // so the tabs are only assigned here.
        CreativeTabs tab = ComputerCraft.mainCreativeTab;
        if (tab != null) {
            for (Item item : new Item[]{ModItems.SECURE_COMPUTER, ModItems.SECURE_TURTLE,
                    ModItems.SECURE_MONITOR, ModItems.SECURE_MANIPULATOR}) {
                item.setCreativeTab(tab);
            }
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandSecureCC());
    }
}

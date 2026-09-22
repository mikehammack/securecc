package com.maximarcana.securecc;

import com.maximarcana.securecc.block.BlockSecureComputer;
import com.maximarcana.securecc.block.BlockSecureManipulator;
import com.maximarcana.securecc.block.BlockSecureMonitor;
import com.maximarcana.securecc.block.BlockSecureTurtle;
import com.maximarcana.securecc.tile.TileSecureComputer;
import com.maximarcana.securecc.tile.TileSecureMonitor;
import com.maximarcana.securecc.tile.TileSecureTurtle;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ModBlocks {
    public static final Block SECURE_COMPUTER = new BlockSecureComputer();
    public static final Block SECURE_TURTLE = new BlockSecureTurtle();
    public static final Block SECURE_MONITOR = new BlockSecureMonitor();
    public static final Block SECURE_MANIPULATOR = new BlockSecureManipulator();

    @Mod.EventBusSubscriber(modid = SecureCC.MODID)
    public static class Registration {
        @SubscribeEvent
        public static void onBlockRegistry(RegistryEvent.Register<Block> event) {
            if (SecureConfig.enableComputer) event.getRegistry().register(SECURE_COMPUTER);
            if (SecureConfig.enableTurtle) event.getRegistry().register(SECURE_TURTLE);
            if (SecureConfig.enableMonitor) event.getRegistry().register(SECURE_MONITOR);
            if (SecureConfig.enableManipulator) event.getRegistry().register(SECURE_MANIPULATOR);
            GameRegistry.registerTileEntity(TileSecureComputer.class,
                    new ResourceLocation(SecureCC.MODID, "secure_computer"));
            GameRegistry.registerTileEntity(TileSecureTurtle.class,
                    new ResourceLocation(SecureCC.MODID, "secure_turtle"));
            GameRegistry.registerTileEntity(TileSecureMonitor.class,
                    new ResourceLocation(SecureCC.MODID, "secure_monitor"));
            // Note: TileManipulator is already registered by Plethora itself;
            // registering it again causes "value already present". Our block
            // creates genuine TileManipulator instances via createNewTileEntity,
            // which works because Plethora's registration covers the class.
        }
    }
}

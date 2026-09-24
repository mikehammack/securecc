package com.maximarcana.securecc;

import com.maximarcana.securecc.block.BlockSecureCable;
import com.maximarcana.securecc.block.BlockSecureComputer;
import com.maximarcana.securecc.block.BlockSecureManipulator;
import com.maximarcana.securecc.block.BlockSecureModem;
import com.maximarcana.securecc.block.BlockSecureMonitor;
import com.maximarcana.securecc.block.BlockSecureTurtle;
import com.maximarcana.securecc.tile.TileSecureCable;
import com.maximarcana.securecc.tile.TileSecureComputer;
import com.maximarcana.securecc.tile.TileSecureModem;
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
    public static final Block SECURE_CABLE = new BlockSecureCable();
    public static final Block SECURE_MODEM = new BlockSecureModem();

    @Mod.EventBusSubscriber(modid = SecureCC.MODID)
    public static class Registration {
        @SubscribeEvent
        public static void onBlockRegistry(RegistryEvent.Register<Block> event) {
            if (SecureConfig.enableComputer) event.getRegistry().register(SECURE_COMPUTER);
            if (SecureConfig.enableTurtle) event.getRegistry().register(SECURE_TURTLE);
            if (SecureConfig.enableMonitor) event.getRegistry().register(SECURE_MONITOR);
            if (SecureConfig.enableManipulator) event.getRegistry().register(SECURE_MANIPULATOR);
            if (SecureConfig.enableCable) event.getRegistry().register(SECURE_CABLE);
            if (SecureConfig.enableModem) event.getRegistry().register(SECURE_MODEM);
            GameRegistry.registerTileEntity(TileSecureCable.class,
                    new ResourceLocation(SecureCC.MODID, "secure_cable"));
            GameRegistry.registerTileEntity(TileSecureModem.class,
                    new ResourceLocation(SecureCC.MODID, "secure_modem"));
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

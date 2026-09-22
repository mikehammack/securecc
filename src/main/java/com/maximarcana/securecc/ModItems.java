package com.maximarcana.securecc;

import com.maximarcana.securecc.block.ItemSecureComputer;
import com.maximarcana.securecc.block.ItemSecureManipulator;
import com.maximarcana.securecc.block.ItemSecureMonitor;
import com.maximarcana.securecc.block.ItemSecureTurtle;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ModItems {
    public static final Item SECURE_COMPUTER = new ItemSecureComputer(ModBlocks.SECURE_COMPUTER);
    public static final Item SECURE_TURTLE = new ItemSecureTurtle(ModBlocks.SECURE_TURTLE);
    public static final Item SECURE_MONITOR = new ItemSecureMonitor(ModBlocks.SECURE_MONITOR);
    public static final Item SECURE_MANIPULATOR = new ItemSecureManipulator(ModBlocks.SECURE_MANIPULATOR);

    @Mod.EventBusSubscriber(modid = SecureCC.MODID)
    public static class Registration {
        @SubscribeEvent
        public static void onItemRegistry(RegistryEvent.Register<Item> event) {
            if (SecureConfig.enableComputer) event.getRegistry().register(SECURE_COMPUTER);
            if (SecureConfig.enableTurtle) event.getRegistry().register(SECURE_TURTLE);
            if (SecureConfig.enableMonitor) event.getRegistry().register(SECURE_MONITOR);
            if (SecureConfig.enableManipulator) event.getRegistry().register(SECURE_MANIPULATOR);
        }

        // Recipe registration is now handled via JSON (assets/securecc/recipes/)
        // with RecipeFactorySecureUpgrade. The RegistryEvent.Register approach
        // did not work in 1.12.2 because IRecipe does not extend
        // IForgeRegistryEntry, preventing a typed subscriber, and raw-type
        // subscribers do not receive the generic event.
    }
}

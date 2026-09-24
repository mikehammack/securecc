package com.maximarcana.securecc;

import com.maximarcana.securecc.block.ItemSecureCable;
import com.maximarcana.securecc.block.ItemSecureComputer;
import com.maximarcana.securecc.block.ItemSecureManipulator;
import com.maximarcana.securecc.block.ItemSecureModem;
import com.maximarcana.securecc.block.ItemSecureMonitor;
import com.maximarcana.securecc.block.ItemSecureTurtle;
import com.maximarcana.securecc.item.ItemSecureNeuralInterface;
import com.maximarcana.securecc.item.ItemSecurityKey;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ModItems {
    public static final Item SECURE_COMPUTER = new ItemSecureComputer(ModBlocks.SECURE_COMPUTER);
    public static final Item SECURE_TURTLE = new ItemSecureTurtle(ModBlocks.SECURE_TURTLE);
    public static final Item SECURE_MONITOR = new ItemSecureMonitor(ModBlocks.SECURE_MONITOR);
    public static final Item SECURE_MANIPULATOR = new ItemSecureManipulator(ModBlocks.SECURE_MANIPULATOR);
    public static final Item SECURE_CABLE = new ItemSecureCable(ModBlocks.SECURE_CABLE);
    public static final Item SECURE_MODEM = new ItemSecureModem(ModBlocks.SECURE_MODEM);
    public static final Item SECURE_NEURAL_INTERFACE = new ItemSecureNeuralInterface();
    public static final Item SECURITY_KEY = new ItemSecurityKey();

    @Mod.EventBusSubscriber(modid = SecureCC.MODID)
    public static class Registration {
        @SubscribeEvent
        public static void onItemRegistry(RegistryEvent.Register<Item> event) {
            if (SecureConfig.enableComputer) event.getRegistry().register(SECURE_COMPUTER);
            if (SecureConfig.enableTurtle) event.getRegistry().register(SECURE_TURTLE);
            if (SecureConfig.enableMonitor) event.getRegistry().register(SECURE_MONITOR);
            if (SecureConfig.enableManipulator) event.getRegistry().register(SECURE_MANIPULATOR);
            if (SecureConfig.enableCable) event.getRegistry().register(SECURE_CABLE);
            if (SecureConfig.enableModem) event.getRegistry().register(SECURE_MODEM);
            if (SecureConfig.enableNeuralInterface) event.getRegistry().register(SECURE_NEURAL_INTERFACE);
            if (SecureConfig.enableSecurityKey) event.getRegistry().register(SECURITY_KEY);
        }

        // Recipe registration is now handled via JSON (assets/securecc/recipes/)
        // with RecipeFactorySecureUpgrade. The RegistryEvent.Register approach
        // did not work in 1.12.2 because IRecipe does not extend
        // IForgeRegistryEntry, preventing a typed subscriber, and raw-type
        // subscribers do not receive the generic event.
    }
}

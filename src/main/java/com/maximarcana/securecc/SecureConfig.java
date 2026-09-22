package com.maximarcana.securecc;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * SecureCC configuration file (config/securecc.cfg).
 */
public class SecureConfig {
    public static boolean enableComputer = true;
    public static boolean enableTurtle = true;
    public static boolean enableMonitor = true;
    public static boolean enableManipulator = true;
    public static float blockHardness = 50.0F;
    public static float blockResistance = 2000.0F;
    public static String defaultPolicy = "LOCK";

    public static void init(FMLPreInitializationEvent event) {
        init(event.getSuggestedConfigurationFile());
    }

    public static void init(java.io.File file) {
        Configuration config = new Configuration(file);
        config.load();

        enableComputer = config.getBoolean("enableSecureComputer", "blocks",
                true, "Enable the secure computer block/item/recipe.");
        enableTurtle = config.getBoolean("enableSecureTurtle", "blocks",
                true, "Enable the secure turtle block/item/recipe.");
        enableMonitor = config.getBoolean("enableSecureMonitor", "blocks",
                true, "Enable the secure monitor block/item/recipe.");
        enableManipulator = config.getBoolean("enableSecureManipulator", "blocks",
                true, "Enable the secure manipulator block/item/recipe.");

        blockHardness = config.getFloat("blockHardness", "blocks",
                50.0F, 1.0F, 500.0F,
                "How hard secure blocks are to mine (vanilla obsidian is 50).");
        blockResistance = config.getFloat("blockResistance", "blocks",
                2000.0F, 1.0F, 3600000.0F,
                "Explosion resistance of secure blocks.");

        defaultPolicy = config.getString("defaultPolicy", "security",
                "LOCK", "Default offline policy for newly placed secure devices.",
                new String[]{"SHUTDOWN", "PIN", "FRIENDS", "LOCK", "STAY_RUNNING"});

        if (config.hasChanged()) config.save();
    }
}

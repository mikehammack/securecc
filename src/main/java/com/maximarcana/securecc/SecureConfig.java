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
    public static boolean enableNeuralInterface = true;
    public static boolean enableSecurityKey = true;
    public static boolean enableCable = true;
    public static boolean enableModem = true;
    public static double modemRange = 256.0D;
    public static float blockHardness = 50.0F;
    public static float blockResistance = 2000.0F;
    public static String defaultPolicy = "LOCK";
    public static boolean allowOpBypass = true;
    public static int pinMaxAttempts = 5;
    public static int pinLockoutSeconds = 300;
    public static boolean allowSneakManage = true;

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
        enableNeuralInterface = config.getBoolean("enableSecureNeuralInterface", "items",
                true, "Enable the secure neural interface item/recipe.");
        enableSecurityKey = config.getBoolean("enableSecurityKey", "items",
                true, "Enable the security key item/recipe.");
        enableCable = config.getBoolean("enableSecureCable", "blocks",
                true, "Enable the secure networking cable block/item/recipe.");
        enableModem = config.getBoolean("enableSecureModem", "blocks",
                true, "Enable the secure wireless modem block/item/recipe.");
        modemRange = config.getFloat("modemRange", "network",
                256.0F, 16.0F, 4096.0F,
                "Wireless range of the secure modem in blocks.");

        blockHardness = config.getFloat("blockHardness", "blocks",
                50.0F, 1.0F, 500.0F,
                "How hard secure blocks are to mine (vanilla obsidian is 50).");
        blockResistance = config.getFloat("blockResistance", "blocks",
                2000.0F, 1.0F, 3600000.0F,
                "Explosion resistance of secure blocks.");

        defaultPolicy = config.getString("defaultPolicy", "security",
                "LOCK", "Default offline policy for newly placed secure devices.",
                new String[]{"SHUTDOWN", "PIN", "FRIENDS", "LOCK", "STAY_RUNNING"});

        allowOpBypass = config.getBoolean("allowOpBypass", "security",
                true, "Ops bypass all access checks (breaking, managing, terminal use). "
                        + "Set to false to lock ops out like everyone else.");
        pinMaxAttempts = config.getInt("pinMaxAttempts", "security",
                5, 1, 100, "Wrong PIN entries before PIN entry is locked out.");
        pinLockoutSeconds = config.getInt("pinLockoutSeconds", "security",
                300, 0, 86400, "How long PIN entry stays locked after too many wrong attempts "
                        + "(seconds). 0 disables the lockout.");
        allowSneakManage = config.getBoolean("allowSneakManage", "security",
                true, "Allow shift-right-click with an empty hand to open the security "
                        + "management GUI. Set to false to require the Security Key item "
                        + "for management access.");

        if (config.hasChanged()) config.save();
    }
}

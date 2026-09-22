package com.maximarcana.securecc.client;

import com.maximarcana.securecc.ModItems;
import com.maximarcana.securecc.SecureCC;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Client-only model registration. Loaded only on the client
 * (value = Side.CLIENT), so dedicated servers never touch these classes.
 */
@Mod.EventBusSubscriber(modid = SecureCC.MODID, value = Side.CLIENT)
public class ClientRegistration {
    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        registerItemModel(ModItems.SECURE_COMPUTER);
        registerItemModel(ModItems.SECURE_TURTLE);
        registerItemModel(ModItems.SECURE_MONITOR);
        registerItemModel(ModItems.SECURE_MANIPULATOR);

        // Bind the secure turtle renderer for diamond texture
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.maximarcana.securecc.tile.TileSecureTurtle.class,
                new SecureTurtleRenderer());
    }

    private static void registerItemModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}

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
        registerItemModel(ModItems.SECURE_NEURAL_INTERFACE);
        registerItemModel(ModItems.SECURITY_KEY);
        registerItemModel(ModItems.SECURE_CABLE);
        registerItemModel(ModItems.SECURE_MODEM);

        // Bind the secure turtle renderer for diamond texture
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                com.maximarcana.securecc.tile.TileSecureTurtle.class,
                new SecureTurtleRenderer());

        // Inject our neural interface render layer into biped mob renderers.
        injectSecureInterfaceLayer();
    }

    /**
     * Add LayerSecureInterface to all RenderLivingBase instances using a
     * ModelBiped, so the secure interface renders on mobs wearing it.
     * Uses reflection because RenderLivingBase.addLayer is protected and
     * RenderManager.entityRenderMap is private.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void injectSecureInterfaceLayer() {
        try {
            java.lang.reflect.Method addLayer = null;
            // Try MCP name first (dev), then SRG name (production).
            try {
                addLayer = net.minecraft.client.renderer.entity.RenderLivingBase.class
                        .getDeclaredMethod("addLayer",
                                net.minecraft.client.renderer.entity.layers.LayerRenderer.class);
            } catch (NoSuchMethodException e) {
                addLayer = net.minecraft.client.renderer.entity.RenderLivingBase.class
                        .getDeclaredMethod("func_177094_a",
                                net.minecraft.client.renderer.entity.layers.LayerRenderer.class);
            }
            addLayer.setAccessible(true);

            java.lang.reflect.Field renderMapField = null;
            try {
                renderMapField = net.minecraft.client.renderer.entity.RenderManager.class
                        .getDeclaredField("entityRenderMap");
            } catch (NoSuchFieldException e) {
                renderMapField = net.minecraft.client.renderer.entity.RenderManager.class
                        .getDeclaredField("field_78729_o"); // SRG for entityRenderMap
            }
            renderMapField.setAccessible(true);

            net.minecraft.client.renderer.entity.RenderManager rm =
                    net.minecraft.client.Minecraft.getMinecraft().getRenderManager();
            java.util.Map<Class<?>, net.minecraft.client.renderer.entity.Render<?>> renderMap =
                    (java.util.Map<Class<?>, net.minecraft.client.renderer.entity.Render<?>>)
                            renderMapField.get(rm);
            for (net.minecraft.client.renderer.entity.Render<?> render :
                    renderMap.values()) {
                if (render instanceof net.minecraft.client.renderer.entity.RenderLivingBase) {
                    net.minecraft.client.renderer.entity.RenderLivingBase living =
                            (net.minecraft.client.renderer.entity.RenderLivingBase) render;
                    net.minecraft.client.model.ModelBase model = living.getMainModel();
                    if (model instanceof net.minecraft.client.model.ModelBiped) {
                        addLayer.invoke(living, new LayerSecureInterface(
                                (net.minecraft.client.model.ModelBiped) model));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("SecureCC: failed to inject interface render layer: " + e);
        }
    }

    private static void registerItemModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}

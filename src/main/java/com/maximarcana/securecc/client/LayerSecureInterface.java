package com.maximarcana.securecc.client;

import com.maximarcana.securecc.item.ItemSecureNeuralInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.squiddev.plethora.gameplay.client.ModelInterface;

/**
 * Renders the Secure Neural Interface on mobs wearing it, mirroring
 * Plethora's LayerInterface. Plethora's layer uses NeuralHelpers.getSlot,
 * which only recognizes Plethora's own item by identity, so our subclass
 * needs its own layer using our worn-interface lookup.
 */
@SideOnly(Side.CLIENT)
public class LayerSecureInterface implements LayerRenderer {
    private final ModelBiped model;

    public LayerSecureInterface(ModelBiped model) {
        this.model = model;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                             float partialTicks, float ageInTicks, float netHeadYaw,
                             float headPitch, float scale) {
        ItemStack worn = ItemSecureNeuralInterface.getWornSecureInterface(entity);
        if (worn.isEmpty()) return;
        if (entity.isPotionActive(MobEffects.INVISIBILITY)) return;

        GlStateManager.pushMatrix();
        // Apply the head's transform so the interface follows head movement.
        model.bipedHead.render(scale);
        // Position the interface on the face (approximate; matches Plethora's
        // placement for biped heads).
        GlStateManager.translate(0.0F, -0.25F, -0.125F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getMinecraft().getTextureManager()
                .bindTexture(ModelInterface.TEXTURE_RESOURCE);
        ModelInterface iface = ModelInterface.getNormal();
        ModelInterface.setRotateAngle(iface.bipedHead, 0.0F, 0.0F, 0.0F);
        iface.bipedHead.render(scale);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}

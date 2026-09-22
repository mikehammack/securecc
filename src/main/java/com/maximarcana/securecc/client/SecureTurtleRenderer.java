package com.maximarcana.securecc.client;

import com.maximarcana.securecc.tile.TileSecureTurtle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

import com.maximarcana.securecc.ModItems;

/**
 * Renders the secure turtle with the diamond texture. Uses RenderItem
 * to draw the turtle's item model, which handles lighting correctly.
 */
public class SecureTurtleRenderer extends TileEntitySpecialRenderer<TileSecureTurtle> {
    @Override
    public void render(TileSecureTurtle turtle, double x, double y, double z,
                      float partialTicks, int destroyStage, float alpha) {
        if (turtle == null || turtle.getWorld() == null) return;

        GlStateManager.pushMatrix();
        // Center in block
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        
        // Rotate to face direction (model faces north by default)
        switch (turtle.getDirection()) {
            case SOUTH: GlStateManager.rotate(180, 0, 1, 0); break;
            case WEST: GlStateManager.rotate(90, 0, 1, 0); break;
            case EAST: GlStateManager.rotate(270, 0, 1, 0); break;
            default: break; // NORTH
        }

        // Render the secure turtle item model
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        ItemStack stack = new ItemStack(ModItems.SECURE_TURTLE);
        renderItem.renderItem(stack, ItemCameraTransforms.TransformType.NONE);

        GlStateManager.popMatrix();
    }
}

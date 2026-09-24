package com.maximarcana.securecc.client;

import com.maximarcana.securecc.tile.TileSecureTurtle;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.client.render.TileEntityTurtleRenderer;
import dan200.computercraft.shared.util.Holiday;
import dan200.computercraft.shared.util.HolidayUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.apache.commons.lang3.tuple.Pair;

import javax.vecmath.Matrix4f;
import java.util.List;

/**
 * Renders the secure turtle with its secure (diamond) body model, following
 * CC:Tweaked 1.89.2's {@code TileEntityTurtleRenderer} transform exactly:
 * <pre>
 *   translate(pos + renderOffset)
 *   translate(0.5, 0.5, 0.5)
 *   rotate(180 - yaw, 0, 1, 0)
 *   translate(-0.5, -0.5, -0.5)
 * </pre>
 * then the body, seasonal overlay, and left/right upgrade models. There is
 * deliberately no scaling: the baked "inventory"-variant model already spans
 * the full block, and an earlier revision's stray {@code scale(0.5)} is what
 * shrank the turtle into a corner of the block.
 */
public class SecureTurtleRenderer extends TileEntitySpecialRenderer<TileSecureTurtle> {
    /** LWJGL's GL_QUADS; LWJGL is not on this compile toolchain's classpath. */
    private static final int GL_QUADS = 7;
    private static final ModelResourceLocation SECURE_TURTLE_MODEL =
            new ModelResourceLocation("securecc:secure_turtle", "inventory");

    @Override
    public void render(TileSecureTurtle turtle, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (turtle == null || turtle.getWorld() == null) return;

        // Label nameplate, same rule as vanilla turtles.
        String label = turtle.createProxy().getLabel();
        if (label != null && rendererDispatcher.cameraHitResult != null
                && turtle.getPos().equals(rendererDispatcher.cameraHitResult.getBlockPos())) {
            setLightmapDisabled(true);
            EntityRenderer.drawNameplate(
                    getFontRenderer(), label,
                    (float) x + 0.5F, (float) y + 1.2F, (float) z + 0.5F, 0,
                    rendererDispatcher.entityYaw, rendererDispatcher.entityPitch, false, false);
            setLightmapDisabled(false);
        }

        GlStateManager.pushMatrix();
        try {
            IBlockState state = turtle.getWorld().getBlockState(turtle.getPos());
            Vec3d offset = turtle.getRenderOffset(partialTicks);
            float yaw = turtle.getRenderYaw(partialTicks);
            GlStateManager.translate(x + offset.x, y + offset.y, z + offset.z);

            // Center the block on the origin before rotating, then move back.
            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            GlStateManager.rotate(180.0F - yaw, 0.0F, 1.0F, 0.0F);
            if (label != null && (label.equals("Dinnerbone") || label.equals("Grumm"))) {
                // Flip the model and swap the cull face as winding order will have changed.
                GlStateManager.scale(1.0F, -1.0F, 1.0F);
                GlStateManager.cullFace(GlStateManager.CullFace.FRONT);
            }
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);

            // Body: the secure model (turtle_base geometry, secure texture).
            int colour = turtle.getColour();
            renderModel(state, SECURE_TURTLE_MODEL, colour == -1 ? null : new int[]{colour});

            // Seasonal overlay, same rule as vanilla turtles.
            ModelResourceLocation overlayModel = TileEntityTurtleRenderer.getTurtleOverlayModel(
                    turtle.getOverlay(),
                    HolidayUtil.getCurrentHoliday() == Holiday.Christmas);
            if (overlayModel != null) {
                GlStateManager.disableCull();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                try {
                    renderModel(state, overlayModel, null);
                } finally {
                    GlStateManager.disableBlend();
                    GlStateManager.enableCull();
                }
            }

            // Left/right upgrades (pickaxe, modem, speaker, ...).
            renderUpgrade(state, turtle, TurtleSide.Left, partialTicks);
            renderUpgrade(state, turtle, TurtleSide.Right, partialTicks);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        }
    }

    /** Mirrors CC's private upgrade rendering via public turtle/upgrade APIs. */
    private static void renderUpgrade(IBlockState state, TileSecureTurtle turtle,
                                      TurtleSide side, float partialTicks) {
        ITurtleUpgrade upgrade = turtle.getUpgrade(side);
        if (upgrade == null) return;
        GlStateManager.pushMatrix();
        try {
            float toolAngle = turtle.getToolRenderAngle(side, partialTicks);
            GlStateManager.translate(0.0F, 0.5F, 0.5F);
            GlStateManager.rotate(-toolAngle, 1.0F, 0.0F, 0.0F);
            GlStateManager.translate(0.0F, -0.5F, -0.5F);
            Pair<IBakedModel, Matrix4f> model = upgrade.getModel(turtle.getAccess(), side);
            if (model != null) {
                if (model.getRight() != null) {
                    ForgeHooksClient.multiplyCurrentGlMatrix(model.getRight());
                }
                if (model.getLeft() != null) {
                    renderModel(state, model.getLeft(), null);
                }
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void renderModel(IBlockState state, ModelResourceLocation location, int[] tints) {
        Minecraft mc = Minecraft.getMinecraft();
        ModelManager modelManager = mc.getRenderItem().getItemModelMesher().getModelManager();
        renderModel(state, modelManager.getModel(location), tints);
    }

    private static void renderModel(IBlockState state, IBakedModel model, int[] tints) {
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tessellator = Tessellator.getInstance();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        renderQuads(tessellator, model.getQuads(state, null, 0L), tints);
        for (EnumFacing facing : EnumFacing.values()) {
            renderQuads(tessellator, model.getQuads(state, facing, 0L), tints);
        }
    }

    private static void renderQuads(Tessellator tessellator, List<BakedQuad> quads, int[] tints) {
        BufferBuilder buffer = tessellator.getBuffer();
        VertexFormat format = DefaultVertexFormats.ITEM;
        buffer.begin(GL_QUADS, format);
        for (BakedQuad quad : quads) {
            VertexFormat quadFormat = quad.getFormat();
            if (quadFormat != format) {
                tessellator.draw();
                format = quadFormat;
                buffer.begin(GL_QUADS, format);
            }
            int colour = 0xFFFFFFFF;
            if (quad.hasTintIndex() && tints != null) {
                int index = quad.getTintIndex();
                if (index >= 0 && index < tints.length) {
                    colour = tints[index] | 0xFF000000;
                }
            }
            LightUtil.renderQuadColor(buffer, quad, colour);
        }
        tessellator.draw();
    }
}

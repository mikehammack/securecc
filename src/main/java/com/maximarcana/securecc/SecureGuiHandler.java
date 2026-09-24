package com.maximarcana.securecc;

import com.maximarcana.securecc.client.GuiSecureManage;
import com.maximarcana.securecc.item.ItemSecureNeuralInterface;
import com.maximarcana.securecc.net.ManageTarget;
import com.maximarcana.securecc.neural.SecureNeuralContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.squiddev.plethora.gameplay.client.gui.GuiNeuralInterface;

/**
 * GUI handler for the management screen (friends, PIN, policy) and for the
 * secure neural interface's computer screen.
 *
 * <p>The management screen can be bound to a secure block
 * ({@link #GUI_MANAGE}) or to a worn secure neural interface
 * ({@link #GUI_MANAGE_ENTITY}). The screen itself is containerless (the
 * client builds it and pulls its data over the packet channel), but the
 * server side must still return a non-null Container:
 * FMLNetworkHandler.openGui silently skips sending the OpenGui packet when
 * getServerGuiElement returns null, so the GUI would never open.
 *
 * <p>The neural computer screens ({@link #GUI_SECURE_NEURAL} for the
 * player's own worn interface, {@link #GUI_SECURE_NEURAL_ENTITY} for a
 * mob's) reuse Plethora's container and client GUI directly: Plethora's
 * connector handler cannot open them for our subclass because its slot
 * lookup only recognizes its own registered item, so our event handlers
 * open them through this path after the SecureCC auth check.
 */
public class SecureGuiHandler implements IGuiHandler {
    public static final int GUI_MANAGE = 0;
    public static final int GUI_MANAGE_ENTITY = 1;
    public static final int GUI_SECURE_NEURAL = 2;
    public static final int GUI_SECURE_NEURAL_ENTITY = 3;

    /**
     * Open the management GUI for a block target. Server side only; mirrors
     * the call the shift-right-click handler makes.
     */
    public static void openForBlock(EntityPlayer player, World world, BlockPos pos) {
        // NB: EntityPlayer.openGui is added by Forge's runtime patches, so it
        // is not visible to this compile toolchain; call the Forge network
        // handler it delegates to directly.
        FMLNetworkHandler.openGui(player, SecureCC.instance, GUI_MANAGE,
                world, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Open the management GUI for a worn-interface target (entity id rides
     * in x). Server side only.
     */
    public static void openForEntity(EntityPlayer player, World world, int entityId) {
        FMLNetworkHandler.openGui(player, SecureCC.instance, GUI_MANAGE_ENTITY,
                world, entityId, 0, 0);
    }

    /**
     * Open the neural computer screen for the player's own worn secure
     * neural interface. Server side only; the caller must have passed the
     * SecureCC auth check first.
     */
    public static void openNeuralPlayer(EntityPlayer player) {
        FMLNetworkHandler.openGui(player, SecureCC.instance, GUI_SECURE_NEURAL,
                player.getEntityWorld(), 0, 0, 0);
    }

    /**
     * Open the neural computer screen for a mob's (or another entity's)
     * worn secure neural interface; the entity id rides in x. Server side
     * only; the caller must have passed the SecureCC auth check first.
     */
    public static void openNeuralEntity(EntityPlayer player, EntityLivingBase target) {
        FMLNetworkHandler.openGui(player, SecureCC.instance, GUI_SECURE_NEURAL_ENTITY,
                player.getEntityWorld(), target.getEntityId(), 0, 0);
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_SECURE_NEURAL || id == GUI_SECURE_NEURAL_ENTITY) {
            EntityLivingBase target = neuralTarget(id, player, world, x);
            if (target == null) return null;
            ItemStack worn = ItemSecureNeuralInterface.getWornSecureInterface(target);
            if (worn.isEmpty()) return null;
            return new SecureNeuralContainer(player.inventory, target, worn);
        }
        // The management screen is containerless by design, but Forge 1.12.2
        // only sends the OpenGui packet when this returns a non-null
        // Container, so hand back a dummy one. The client builds the real
        // screen in getClientGuiElement below.
        return new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer playerIn) {
                return true;
            }
        };
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_SECURE_NEURAL || id == GUI_SECURE_NEURAL_ENTITY) {
            EntityLivingBase target = neuralTarget(id, player, world, x);
            if (target == null) return null;
            ItemStack worn = ItemSecureNeuralInterface.getWornSecureInterface(target);
            if (worn.isEmpty()) return null;
            return new GuiNeuralInterface(
                    new SecureNeuralContainer(player.inventory, target, worn));
        }
        int dimension = world.provider.getDimensionType().getId();
        if (id == GUI_MANAGE) {
            return new GuiSecureManage(ManageTarget.forBlock(dimension, new BlockPos(x, y, z)));
        }
        if (id == GUI_MANAGE_ENTITY) {
            return new GuiSecureManage(ManageTarget.forEntity(dimension, x));
        }
        return null;
    }

    /**
     * Resolve the entity wearing the secure neural interface for a neural
     * GUI id: the player themself for {@link #GUI_SECURE_NEURAL}, the entity
     * whose id rides in x for {@link #GUI_SECURE_NEURAL_ENTITY}.
     */
    private static EntityLivingBase neuralTarget(int id, EntityPlayer player,
                                                 World world, int x) {
        if (id == GUI_SECURE_NEURAL) return player;
        Entity entity = world.getEntityByID(x);
        return entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
    }
}

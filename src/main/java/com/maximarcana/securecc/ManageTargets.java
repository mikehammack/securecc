package com.maximarcana.securecc;

import com.maximarcana.securecc.item.ItemSecureNeuralInterface;
import com.maximarcana.securecc.net.ManageTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.squiddev.plethora.gameplay.neural.NeuralHelpers;

/**
 * Server-side resolution of a {@link ManageTarget} to its mutable
 * {@link SecureAccess}.
 *
 * <ul>
 * <li>Block targets resolve to the live access (tile or manipulator auth
 * data); mutations mark dirty through the usual path, so saving is a
 * no-op.</li>
 * <li>Entity targets resolve to a detached copy loaded from the worn
 * secure neural interface's stack NBT; {@link Resolved#save()} writes it
 * back to the stack.</li>
 * </ul>
 */
public final class ManageTargets {
    private ManageTargets() {
    }

    public static final class Resolved {
        public final SecureAccess access;
        /** Display title for the GUI; null means "use the default". */
        public final String title;
        private final Runnable onSave;

        private Resolved(SecureAccess access, String title, Runnable onSave) {
            this.access = access;
            this.title = title;
            this.onSave = onSave;
        }

        public void save() {
            onSave.run();
        }
    }

    /** Resolve the target, or null when it no longer has secure access. */
    public static Resolved resolve(World world, ManageTarget target) {
        if (world == null || target == null) return null;
        if (target.isEntity) {
            Entity entity = world.getEntityByID(target.entityId);
            if (!(entity instanceof EntityLivingBase)) return null;
            ItemStack worn = ItemSecureNeuralInterface.getWornSecureInterface(
                    (EntityLivingBase) entity);
            if (worn.isEmpty()) return null;
            ItemSecureNeuralInterface item =
                    (ItemSecureNeuralInterface) worn.getItem();
            SecureAccess access = item.getAccess(worn);
            Runnable save = new Runnable() {
                @Override
                public void run() {
                    item.saveAccess(worn, access);
                }
            };
            return new Resolved(access, entity.getName(), save);
        }
        SecureAccess access = SecureAccess.forBlock(world, target.pos);
        if (access == null) return null;
        return new Resolved(access, null, new Runnable() {
            @Override
            public void run() {
                // Live access: mutations already mark the tile/auth data dirty.
            }
        });
    }
}

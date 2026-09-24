package com.maximarcana.securecc.net;

import com.maximarcana.securecc.SecureAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import com.maximarcana.securecc.ManageTargets;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Client -&gt; server: add a friend to the management target. */
public class AddFriendMessage implements IMessage {
    public ManageTarget target;
    public String name;

    public AddFriendMessage() {
    }

    public AddFriendMessage(ManageTarget target, String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = ManageTarget.fromBytes(buf);
        name = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        target.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, name);
    }

    public static class Handler implements IMessageHandler<AddFriendMessage, IMessage> {
        /**
         * Single-thread executor for Mojang profile lookups. The lookup does
         * blocking network I/O (up to ~6s of timeouts), so it must never run
         * on the server thread — a slow API response would stall the entire
         * server.
         */
        private static final Executor MOJANG_EXECUTOR = Executors.newSingleThreadExecutor(
                new java.util.concurrent.ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "SecureCC-MojangLookup");
                        t.setDaemon(true);
                        return t;
                    }
                });

        @Override
        public IMessage onMessage(AddFriendMessage msg, MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = sender.getServerWorld();
                    ManageTargets.Resolved resolved = ManageTargets.resolve(world, msg.target);
                    if (resolved == null) {
                        SecureNetwork.reply(sender, "Could not find that secure device.");
                        return;
                    }
                    SecureAccess access = resolved.access;
                    if (!access.canManage(sender)) {
                        SecureNetwork.reply(sender, "Only the owner (or an op) can manage friends.");
                        return;
                    }
                    if (!SecureAccess.isValidPlayerName(msg.name)) {
                        SecureNetwork.reply(sender, "Invalid player name.");
                        return;
                    }
                    MinecraftServer server = sender.getServer();
                    // Fast paths (online player, offline-mode UUID) resolve
                    // instantly. Online-mode + offline player needs the
                    // Mojang API, which runs off-thread.
                    UUID fastId = SecureAccess.resolveFriendUUIDFast(server, msg.name);
                    if (fastId != null) {
                        finishAddFriend(sender, msg.target, resolved, msg.name, fastId);
                    } else {
                        SecureNetwork.reply(sender, "Looking up " + msg.name + "...");
                        MOJANG_EXECUTOR.execute(new Runnable() {
                            @Override
                            public void run() {
                                final UUID mojangId = SecureAccess.lookupMojangUUID(msg.name);
                                server.addScheduledTask(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Re-resolve and re-check: the world
                                        // may have changed during the lookup.
                                        ManageTargets.Resolved r2 = ManageTargets.resolve(
                                                sender.getServerWorld(), msg.target);
                                        if (r2 == null || !r2.access.canManage(sender)) return;
                                        finishAddFriend(sender, msg.target, r2, msg.name, mojangId);
                                    }
                                });
                            }
                        });
                    }
                }
            });
            return null;
        }

        /** Shared tail of the add: UUID or name-only entry, save, sync. */
        private static void finishAddFriend(EntityPlayerMP sender,
                                           ManageTarget target,
                                           ManageTargets.Resolved resolved,
                                           String name, UUID id) {
            SecureAccess access = resolved.access;
            String display = name;
            EntityPlayerMP online = sender.getServer().getPlayerList().getPlayerByUsername(name);
            if (online != null) display = online.getName();
            if (id != null) {
                access.addFriend(id, display);
                SecureNetwork.reply(sender, display + " added as a friend.");
            } else {
                access.addFriendName(display);
                SecureNetwork.reply(sender, display + " added by name "
                        + "(offline lookup unavailable; matched by name).");
            }
            resolved.save();
            SecureNetwork.syncManage(sender, sender.getServerWorld(), target, resolved);
        }
    }
}

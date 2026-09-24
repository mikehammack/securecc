package com.maximarcana.securecc.net;

import com.maximarcana.securecc.SecureAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import com.maximarcana.securecc.ManageTargets;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -&gt; server: remove a friend from the management target. */
public class RemoveFriendMessage implements IMessage {
    public ManageTarget target;
    public String name;

    public RemoveFriendMessage() {
    }

    public RemoveFriendMessage(ManageTarget target, String name) {
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

    public static class Handler implements IMessageHandler<RemoveFriendMessage, IMessage> {
        @Override
        public IMessage onMessage(RemoveFriendMessage msg, MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = sender.getServerWorld();
                    ManageTargets.Resolved resolved = ManageTargets.resolve(world, msg.target);
                    if (resolved == null) return;
                    SecureAccess access = resolved.access;
                    if (!access.canManage(sender)) {
                        SecureNetwork.reply(sender, "Only the owner (or an op) can manage friends.");
                        return;
                    }
                    access.removeFriend(msg.name);
                    SecureNetwork.reply(sender, msg.name + " removed.");
                    resolved.save();
                    SecureNetwork.syncManage(sender, world, msg.target, resolved);
                }
            });
            return null;
        }
    }
}

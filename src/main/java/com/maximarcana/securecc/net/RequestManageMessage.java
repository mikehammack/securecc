package com.maximarcana.securecc.net;

import com.maximarcana.securecc.SecureAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import com.maximarcana.securecc.ManageTargets;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -&gt; server: send me the full management state for this block. */
public class RequestManageMessage implements IMessage {
    public ManageTarget target;

    public RequestManageMessage() {
    }

    public RequestManageMessage(ManageTarget target) {
        this.target = target;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = ManageTarget.fromBytes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        target.toBytes(buf);
    }

    public static class Handler implements IMessageHandler<RequestManageMessage, IMessage> {
        @Override
        public IMessage onMessage(RequestManageMessage msg, MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = sender.getServerWorld();
                    ManageTargets.Resolved resolved =
                            ManageTargets.resolve(world, msg.target);
                    if (resolved == null || !resolved.access.canManage(sender)) return;
                    SecureNetwork.syncManage(sender, world, msg.target, resolved);
                }
            });
            return null;
        }
    }
}

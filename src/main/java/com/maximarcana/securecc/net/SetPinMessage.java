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

/**
 * Client -&gt; server: set or remove the target's PIN. An empty PIN removes it.
 * Mirrors the /securecc pin rules (minimum 4 characters when setting).
 */
public class SetPinMessage implements IMessage {
    public ManageTarget target;
    public String pin = "";

    public SetPinMessage() {
    }

    public SetPinMessage(ManageTarget target, String pin) {
        this.target = target;
        this.pin = pin == null ? "" : pin;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = ManageTarget.fromBytes(buf);
        pin = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        target.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, pin);
    }

    public static class Handler implements IMessageHandler<SetPinMessage, IMessage> {
        @Override
        public IMessage onMessage(SetPinMessage msg, MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = sender.getServerWorld();
                    ManageTargets.Resolved resolved = ManageTargets.resolve(world, msg.target);
                    if (resolved == null) return;
                    SecureAccess access = resolved.access;
                    if (!access.canManage(sender)) {
                        SecureNetwork.reply(sender, "Only the owner (or an op) can change the PIN.");
                        return;
                    }
                    if (msg.pin.isEmpty()) {
                        access.setPin(null);
                        SecureNetwork.reply(sender, "PIN removed.");
                    } else {
                        if (msg.pin.length() < 4) {
                            SecureNetwork.reply(sender, "PIN must be at least 4 characters.");
                            return;
                        }
                        if (msg.pin.length() > 16) {
                            SecureNetwork.reply(sender, "PIN must be at most 16 characters.");
                            return;
                        }
                        access.setPin(msg.pin);
                        SecureNetwork.reply(sender, "PIN set.");
                    }
                    resolved.save();
                    SecureNetwork.syncManage(sender, world, msg.target, resolved);
                }
            });
            return null;
        }
    }
}

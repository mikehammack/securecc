package com.maximarcana.securecc.net;

import com.maximarcana.securecc.Policy;
import com.maximarcana.securecc.SecureAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import com.maximarcana.securecc.ManageTargets;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -&gt; server: set the target's single access policy. */
public class SetPolicyMessage implements IMessage {
    public ManageTarget target;
    public int policyId;

    public SetPolicyMessage() {
    }

    public SetPolicyMessage(ManageTarget target, int policyId) {
        this.target = target;
        this.policyId = policyId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = ManageTarget.fromBytes(buf);
        policyId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        target.toBytes(buf);
        buf.writeInt(policyId);
    }

    public static class Handler implements IMessageHandler<SetPolicyMessage, IMessage> {
        @Override
        public IMessage onMessage(SetPolicyMessage msg, MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = sender.getServerWorld();
                    ManageTargets.Resolved resolved = ManageTargets.resolve(world, msg.target);
                    if (resolved == null) return;
                    SecureAccess access = resolved.access;
                    if (!access.canManage(sender)) {
                        SecureNetwork.reply(sender, "Only the owner (or an op) can change the policy.");
                        return;
                    }
                    Policy policy = Policy.byId(msg.policyId);
                    access.setPolicy(policy);
                    SecureNetwork.reply(sender, "Policy set to " + policy.display + ".");
                    resolved.save();
                    SecureNetwork.syncManage(sender, world, msg.target, resolved);
                }
            });
            return null;
        }
    }
}

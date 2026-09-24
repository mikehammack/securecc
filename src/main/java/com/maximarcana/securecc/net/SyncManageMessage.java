package com.maximarcana.securecc.net;

import com.maximarcana.securecc.client.ClientManageCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -&gt; client: the full management state for a target (owner, friends,
 * PIN state, lockout, policy, display title). Applied on the client thread
 * into {@link ClientManageCache}; the client-only classes are only touched
 * inside the scheduled task, so this handler is safe to load on a dedicated
 * server.
 */
public class SyncManageMessage implements IMessage {
    public ManageTarget target;
    /** Display title; null means "use the default block title". */
    public String title;
    public String ownerName;
    public List<String> friends = new ArrayList<String>();
    public boolean hasPin;
    public long pinLockoutSeconds;
    public int policyId;

    public SyncManageMessage() {
    }

    public SyncManageMessage(ManageTarget target, String title, String ownerName, List<String> friends,
                             boolean hasPin, long pinLockoutSeconds, int policyId) {
        this.target = target;
        this.title = title;
        this.ownerName = ownerName;
        this.friends = new ArrayList<String>(friends);
        this.hasPin = hasPin;
        this.pinLockoutSeconds = pinLockoutSeconds;
        this.policyId = policyId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = ManageTarget.fromBytes(buf);
        title = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
        ownerName = ByteBufUtils.readUTF8String(buf);
        int n = buf.readInt();
        friends = new ArrayList<String>(n);
        for (int i = 0; i < n; i++) friends.add(ByteBufUtils.readUTF8String(buf));
        hasPin = buf.readBoolean();
        pinLockoutSeconds = buf.readLong();
        policyId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        target.toBytes(buf);
        buf.writeBoolean(title != null);
        if (title != null) ByteBufUtils.writeUTF8String(buf, title);
        ByteBufUtils.writeUTF8String(buf, ownerName);
        buf.writeInt(friends.size());
        for (String f : friends) ByteBufUtils.writeUTF8String(buf, f);
        buf.writeBoolean(hasPin);
        buf.writeLong(pinLockoutSeconds);
        buf.writeInt(policyId);
    }

    public static class Handler implements IMessageHandler<SyncManageMessage, IMessage> {
        @Override
        public IMessage onMessage(final SyncManageMessage msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ClientManageCache.apply(msg.target, msg.title, msg.ownerName, msg.friends,
                            msg.hasPin, msg.pinLockoutSeconds, msg.policyId);
                }
            });
            return null;
        }
    }
}

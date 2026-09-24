package com.maximarcana.securecc.net;

import com.maximarcana.securecc.ManageTargets;
import com.maximarcana.securecc.SecureAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Packet channel for the block-management GUI. All mutations are verified
 * server-side (sender must be able to manage the block).
 */
public class SecureNetwork {
    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("securecc");

    public static void init() {
        CHANNEL.registerMessage(AddFriendMessage.Handler.class, AddFriendMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(RemoveFriendMessage.Handler.class, RemoveFriendMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(RequestManageMessage.Handler.class, RequestManageMessage.class, 2, Side.SERVER);
        CHANNEL.registerMessage(SyncManageMessage.Handler.class, SyncManageMessage.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(SetPinMessage.Handler.class, SetPinMessage.class, 4, Side.SERVER);
        CHANNEL.registerMessage(SetPolicyMessage.Handler.class, SetPolicyMessage.class, 5, Side.SERVER);
    }

    public static void reply(EntityPlayerMP player, String msg) {
        player.sendMessage(new TextComponentString("\u00a7b[SecureCC]\u00a7r " + msg));
    }

    public static void syncManage(EntityPlayerMP player, World world,
                                 ManageTarget target, ManageTargets.Resolved resolved) {
        SecureAccess access = resolved.access;
        String ownerName = access.getOwnerName();
        long lockout = access.isPinLockedOut() ? access.getPinLockoutRemainingSeconds() : 0;
        CHANNEL.sendTo(new SyncManageMessage(target, resolved.title,
                ownerName == null ? "" : ownerName, access.getFriendNameList(),
                access.hasPin(), lockout, access.getPolicy().id), player);
    }
}

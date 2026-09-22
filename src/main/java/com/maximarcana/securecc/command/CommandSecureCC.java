package com.maximarcana.securecc.command;

import com.maximarcana.securecc.ISecureTile;
import com.maximarcana.securecc.Policy;
import com.maximarcana.securecc.SecureAccess;
import com.maximarcana.securecc.block.BlockSecureManipulator;
import com.maximarcana.securecc.tile.TileSecureMonitor;
import com.maximarcana.securecc.tile.TileSecureTurtle;
import com.maximarcana.securecc.manip.ManipulatorAuthData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /securecc — manage the secure block you are looking at.
 *
 *   /securecc policy <stay|lock|pin|friends|shutdown>
 *   /securecc pin set <pin>      (owner only)
 *   /securecc pin <pin>          (enter someone's PIN while they are away)
 *   /securecc friend <add|remove> <player>
 *   /securecc info
 */
public class CommandSecureCC extends CommandBase {

    /** A secure block the player is looking at, with its access state. */
    private static class Target {
        final SecureAccess access;
        final String kind;
        final Runnable onPolicyChanged;

        Target(SecureAccess access, String kind, Runnable onPolicyChanged) {
            this.access = access;
            this.kind = kind;
            this.onPolicyChanged = onPolicyChanged;
        }
    }

    @Override
    public String getName() {
        return "securecc";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/securecc <policy|pin|friend|info> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private Target target(MinecraftServer server, ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new CommandException("Only players can use this command.");
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        RayTraceResult ray = player.world.rayTraceBlocks(eyes,
                eyes.add(look.x * 6.0D, look.y * 6.0D, look.z * 6.0D), false, false, true);
        if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) {
            throw new CommandException("Look at a secure block within 6 blocks.");
        }
        BlockPos pos = ray.getBlockPos();
        TileEntity te = player.world.getTileEntity(pos);
        if (te instanceof ISecureTile) {
            ISecureTile tile = (ISecureTile) te;
            String kind = te instanceof TileSecureTurtle ? "turtle"
                    : te instanceof TileSecureMonitor ? "monitor" : "computer";
            return new Target(tile.getSecureAccess(), kind, tile::applyShutdownPolicy);
        }
        if (player.world.getBlockState(pos).getBlock() instanceof BlockSecureManipulator) {
            ManipulatorAuthData data = ManipulatorAuthData.get(player.world);
            if (data == null) {
                throw new CommandException("Manipulator access data is unavailable.");
            }
            return new Target(data.getOrCreate(pos, player.world), "manipulator", () -> {
            });
        }
        throw new CommandException("That is not a secure block.");
    }

    private void requireManage(SecureAccess access, EntityPlayerMP player) throws CommandException {
        if (!access.canManage(player)) {
            throw new CommandException("Only the owner (or an op) can do that.");
        }
    }

    private void reply(ICommandSender sender, String msg) {
        sender.sendMessage(new TextComponentString("\u00a7b[SecureCC]\u00a7r " + msg));
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));
        Target t = target(server, sender);
        SecureAccess access = t.access;
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "policy": {
                requireManage(access, player);
                if (args.length < 2) throw new WrongUsageException("/securecc policy <stay|lock|pin|friends|shutdown>");
                Policy p = Policy.byArg(args[1]);
                if (p == null) throw new CommandException("Unknown policy. Use: stay, lock, pin, friends, shutdown");
                access.setPolicy(p);
                t.onPolicyChanged.run();
                reply(sender, "Offline policy set to " + p.display + ".");
                break;
            }
            case "pin": {
                if (args.length < 2) throw new WrongUsageException("/securecc pin <set <pin>|<pin>>");
                if (args[1].equalsIgnoreCase("set")) {
                    requireManage(access, player);
                    if (args.length < 3) throw new WrongUsageException("/securecc pin set <pin>");
                    access.setPin(args[2]);
                    reply(sender, "PIN set. With the PIN Access policy, anyone holding the PIN can use this "
                            + t.kind + " while you are offline.");
                } else {
                    if (access.getPin() != null && access.getPin().equals(args[1])) {
                        access.grantPinSession(player.getUniqueID());
                        reply(sender, "PIN accepted. You can use this " + t.kind + " while the owner is away.");
                    } else {
                        throw new CommandException("Wrong PIN.");
                    }
                }
                break;
            }
            case "friend": {
                requireManage(access, player);
                if (args.length < 3) throw new WrongUsageException("/securecc friend <add|remove> <player>");
                // Note: the friend must be online; offline UUID lookup needs
                // authlib, which is not on the build classpath.
                EntityPlayerMP friend = server.getPlayerList().getPlayerByUsername(args[2]);
                if (friend == null) {
                    throw new CommandException("Player is not online: " + args[2]);
                }
                if (args[1].equalsIgnoreCase("add")) {
                    access.addFriend(friend.getUniqueID(), friend.getName());
                    reply(sender, friend.getName() + " added as a friend of this " + t.kind + ".");
                } else if (args[1].equalsIgnoreCase("remove")) {
                    access.removeFriend(friend.getUniqueID());
                    reply(sender, friend.getName() + " removed.");
                } else {
                    throw new WrongUsageException("/securecc friend <add|remove> <player>");
                }
                break;
            }
            case "info": {
                reply(sender, "Owner: " + access.getOwnerName()
                        + " | Policy: " + access.getPolicy().display
                        + " | PIN: " + (access.getPin() != null ? "set" : "not set")
                        + " | Friends: " + access.getFriendNames());
                break;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                         String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "policy", "pin", "friend", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("policy")) {
            return getListOfStringsMatchingLastWord(args, "stay", "lock", "pin", "friends", "shutdown");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("friend")) {
            return getListOfStringsMatchingLastWord(args, "add", "remove");
        }
        return Collections.emptyList();
    }
}

package com.maximarcana.securecc;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owner / policy / friend / PIN state shared by every secure block.
 * Tiles hold one instance and delegate; the Plethora manipulator keeps its
 * instance in a WorldSavedData (its tile class is final and cannot be
 * extended).
 */
public class SecureAccess {
    private UUID ownerId;
    private String ownerName;
    private Policy policy = defaultPolicy();
    private String pin;
    private final Set<UUID> friends = new HashSet<UUID>();
    private final Map<UUID, String> friendNames = new HashMap<UUID, String>();
    /** Players who entered the PIN this session (not persisted). */
    private final Set<UUID> pinSessions = new HashSet<UUID>();
    private Runnable dirtyMark;

    /** Default policy from config (falls back to LOCK if config not loaded). */
    private static Policy defaultPolicy() {
        try {
            return Policy.valueOf(SecureConfig.defaultPolicy);
        } catch (Exception e) {
            return Policy.LOCK;
        }
    }

    public void setDirtyMark(Runnable dirtyMark) {
        this.dirtyMark = dirtyMark;
    }

    private void markChanged() {
        if (dirtyMark != null) dirtyMark.run();
    }

    // ---------- NBT ----------

    public void readFromNBT(NBTTagCompound tag) {
        String oid = tag.getString("secOwner");
        try {
            ownerId = oid.isEmpty() ? null : UUID.fromString(oid);
        } catch (IllegalArgumentException e) {
            ownerId = null;
        }
        ownerName = tag.getString("secOwnerName");
        if (ownerName.isEmpty()) ownerName = null;
        policy = Policy.byId(tag.getInteger("secPolicy"));
        pin = tag.getString("secPin");
        if (pin.isEmpty()) pin = null;
        friends.clear();
        friendNames.clear();
        NBTTagList list = tag.getTagList("secFriends", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            try {
                UUID id = UUID.fromString(c.getString("id"));
                friends.add(id);
                String n = c.getString("name");
                if (!n.isEmpty()) friendNames.put(id, n);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (ownerId != null) {
            tag.setString("secOwner", ownerId.toString());
            if (ownerName != null) tag.setString("secOwnerName", ownerName);
        }
        tag.setInteger("secPolicy", policy.id);
        if (pin != null) tag.setString("secPin", pin);
        NBTTagList list = new NBTTagList();
        for (UUID id : friends) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("id", id.toString());
            String n = friendNames.get(id);
            if (n != null) c.setString("name", n);
            list.appendTag(c);
        }
        tag.setTag("secFriends", list);
        return tag;
    }

    // ---------- Ownership ----------

    public boolean hasOwner() {
        return ownerId != null;
    }

    public String getOwnerName() {
        return ownerName != null ? ownerName : "unknown";
    }

    public void setOwner(EntityPlayer player) {
        ownerId = player.getUniqueID();
        ownerName = player.getName();
        markChanged();
    }

    public boolean isOwner(EntityPlayer player) {
        return ownerId != null && ownerId.equals(player.getUniqueID());
    }

    public static boolean isOp(EntityPlayer player) {
        return player instanceof EntityPlayerMP
                && ((EntityPlayerMP) player).canUseCommand(2, "securecc");
    }

    public boolean isOwnerOnline(World world) {
        return ownerId != null && world != null && world.getMinecraftServer() != null
                && world.getMinecraftServer().getPlayerList().getPlayerByUUID(ownerId) != null;
    }

    // ---------- Friends / PIN ----------

    public boolean isFriend(EntityPlayer player) {
        return friends.contains(player.getUniqueID());
    }

    public void addFriend(UUID id, String name) {
        friends.add(id);
        if (name != null) friendNames.put(id, name);
        markChanged();
    }

    public void removeFriend(UUID id) {
        friends.remove(id);
        friendNames.remove(id);
        pinSessions.remove(id);
        markChanged();
    }

    public String getFriendNames() {
        if (friends.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (UUID id : friends) {
            if (sb.length() > 0) sb.append(", ");
            String n = friendNames.get(id);
            sb.append(n != null ? n : id.toString().substring(0, 8));
        }
        return sb.toString();
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = (pin == null || pin.isEmpty()) ? null : pin;
        markChanged();
    }

    public void grantPinSession(UUID playerId) {
        pinSessions.add(playerId);
    }

    public void revokePinSession(UUID playerId) {
        pinSessions.remove(playerId);
    }

    // ---------- Policy ----------

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy != null ? policy : defaultPolicy();
        markChanged();
    }

    /**
     * Can this player open and use the block right now?
     * (Terminal for computers/turtles, touchscreen for monitors,
     * module GUI for the manipulator.)
     */
    public boolean canUseTerminal(EntityPlayer player, World world) {
        if (!hasOwner()) return true; // unclaimed: vanilla behaviour
        if (isOwner(player) || isOp(player)) return true;
        boolean ownerOnline = isOwnerOnline(world);
        boolean friend = isFriend(player);
        switch (policy) {
            case STAY_RUNNING:
                return friend;
            case LOCK:
                return friend && ownerOnline;
            case PIN:
                return (friend && ownerOnline) || pinSessions.contains(player.getUniqueID());
            case FRIENDS:
                return friend;
            case SHUTDOWN:
                return friend && ownerOnline;
            default:
                return false;
        }
    }

    /** Can this player break the block? Owner or op only. */
    public boolean canBreak(EntityPlayer player) {
        if (!hasOwner()) return true;
        return isOwner(player) || isOp(player);
    }

    /** Can this player change policy/PIN/friends? Owner or op only. */
    public boolean canManage(EntityPlayer player) {
        if (!hasOwner()) return true;
        return isOwner(player) || isOp(player);
    }
}

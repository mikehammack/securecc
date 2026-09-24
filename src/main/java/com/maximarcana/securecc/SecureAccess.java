package com.maximarcana.securecc;

import com.maximarcana.securecc.block.BlockSecureManipulator;
import com.maximarcana.securecc.manip.ManipulatorAuthData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owner / policy / friend / PIN state shared by every secure block.
 * Tiles hold one instance and delegate; the Plethora manipulator keeps its
 * instance in a WorldSavedData (its tile class is final and cannot be
 * extended).
 *
 * 1.1.0 changes:
 * - PINs are stored as salted SHA-256 hashes, never plaintext. Legacy
 *   plaintext PINs are hashed in place on first load (migration).
 * - PIN entry is throttled: too many wrong attempts locks PIN entry for a
 *   configurable duration.
 * - Friends are (UUID, name) pairs; friends added while offline are stored
 *   by name and matched case-insensitively at check time.
 */
public class SecureAccess {
    private UUID ownerId;
    private String ownerName;
    private Policy policy = defaultPolicy();
    /** Hex-encoded SHA-256(salt + pin). Null when no PIN is set. */
    private String pinHash;
    /** Hex-encoded 16-byte salt for the PIN hash. */
    private String pinSalt;
    private final Set<UUID> friends = new HashSet<UUID>();
    private final Map<UUID, String> friendNames = new HashMap<UUID, String>();
    /**
     * Lower-cased names of friends added without a UUID (offline adds on
     * online-mode servers where the Mojang lookup failed).
     */
    private final Set<String> nameFriends = new HashSet<String>();
    /** Lower-cased name -&gt; display-case name, parallel to nameFriends. */
    private final Map<String, String> nameFriendDisplay = new HashMap<String, String>();
    /** Players who entered the PIN this session (not persisted). */
    private final Set<UUID> pinSessions = new HashSet<UUID>();
    /** Transient brute-force state (not persisted; resets on restart). */
    private int pinFails;
    private long pinLockoutUntil;
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
        pinHash = tag.getString("secPinHash");
        if (pinHash.isEmpty()) pinHash = null;
        pinSalt = tag.getString("secPinSalt");
        if (pinSalt.isEmpty()) pinSalt = null;
        // Migration: a legacy plaintext PIN becomes a salted hash on load.
        // The legacy key is removed so plaintext never persists on disk.
        String legacyPin = tag.getString("secPin");
        if (!legacyPin.isEmpty()) {
            if (pinHash == null) setPin(legacyPin);
            tag.removeTag("secPin");
        }
        friends.clear();
        friendNames.clear();
        nameFriends.clear();
        nameFriendDisplay.clear();
        NBTTagList list = tag.getTagList("secFriends", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            String n = c.getString("name");
            if (c.hasKey("id")) {
                try {
                    UUID id = UUID.fromString(c.getString("id"));
                    friends.add(id);
                    if (!n.isEmpty()) friendNames.put(id, n);
                } catch (IllegalArgumentException ignored) {
                }
            } else if (!n.isEmpty()) {
                addFriendName(n);
            }
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (ownerId != null) {
            tag.setString("secOwner", ownerId.toString());
            if (ownerName != null) tag.setString("secOwnerName", ownerName);
        }
        tag.setInteger("secPolicy", policy.id);
        if (pinHash != null) {
            tag.setString("secPinHash", pinHash);
            if (pinSalt != null) tag.setString("secPinSalt", pinSalt);
        }
        // Note: the legacy "secPin" key is never written back.
        NBTTagList list = new NBTTagList();
        for (UUID id : friends) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("id", id.toString());
            String n = friendNames.get(id);
            if (n != null) c.setString("name", n);
            list.appendTag(c);
        }
        for (String lower : nameFriends) {
            NBTTagCompound c = new NBTTagCompound();
            String display = nameFriendDisplay.get(lower);
            c.setString("name", display != null ? display : lower);
            list.appendTag(c);
        }
        tag.setTag("secFriends", list);
        return tag;
    }

    // ---------- Ownership ----------

    public boolean hasOwner() {
        return ownerId != null;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName != null ? ownerName : "unknown";
    }

    public String getOwnerNameOrNull() {
        return ownerName;
    }

    public void setOwner(EntityPlayer player) {
        ownerId = player.getUniqueID();
        ownerName = player.getName();
        markChanged();
    }

    /**
     * Restore the owner from a dropped item stack's NBT (owner persistence
     * across break/place). Returns true when the stack carried owner NBT.
     */
    public boolean restoreOwnerFromStack(ItemStack stack) {
        if (stack != null && stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag.hasKey("secOwner")) {
                try {
                    ownerId = UUID.fromString(tag.getString("secOwner"));
                    String n = tag.getString("secOwnerName");
                    ownerName = n.isEmpty() ? null : n;
                    markChanged();
                    return true;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return false;
    }

    /** Owner NBT for stamping onto a dropped item stack. Null when unowned. */
    public NBTTagCompound writeOwnerTag() {
        if (ownerId == null) return null;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("secOwner", ownerId.toString());
        if (ownerName != null) tag.setString("secOwnerName", ownerName);
        return tag;
    }

    /** Merge this block's owner NBT into a dropped item stack. */
    public void stampOwnerOnto(ItemStack stack) {
        if (ownerId == null || stack == null || stack.isEmpty()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString("secOwner", ownerId.toString());
        if (ownerName != null) tag.setString("secOwnerName", ownerName);
    }

    public boolean isOwner(EntityPlayer player) {
        return ownerId != null && ownerId.equals(player.getUniqueID());
    }

    public static boolean isOp(EntityPlayer player) {
        return SecureConfig.allowOpBypass && player instanceof EntityPlayerMP
                && ((EntityPlayerMP) player).canUseCommand(2, "securecc");
    }

    public boolean isOwnerOnline(World world) {
        return ownerId != null && world != null && world.getMinecraftServer() != null
                && world.getMinecraftServer().getPlayerList().getPlayerByUUID(ownerId) != null;
    }

    /**
     * The SecureAccess for the secure block at this position, or null when
     * the block is not one of ours. Covers computers, turtles, monitors
     * (tile entities) and manipulators (WorldSavedData).
     */
    public static SecureAccess forBlock(World world, BlockPos pos) {
        if (world == null || pos == null) return null;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ISecureTile) {
            return ((ISecureTile) te).getSecureAccess();
        }
        if (te != null && world.getBlockState(pos).getBlock() instanceof BlockSecureManipulator) {
            ManipulatorAuthData data = ManipulatorAuthData.get(world);
            return data == null ? null : data.get(pos, world);
        }
        return null;
    }

    // ---------- Friends ----------

    public boolean isFriend(EntityPlayer player) {
        return friends.contains(player.getUniqueID())
                || nameFriends.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    public void addFriend(UUID id, String name) {
        friends.add(id);
        if (name != null) friendNames.put(id, name);
        markChanged();
    }

    /** Add a friend matched by case-insensitive name at check time. */
    public void addFriendName(String name) {
        if (name == null) return;
        String lower = name.toLowerCase(Locale.ROOT);
        nameFriends.add(lower);
        nameFriendDisplay.put(lower, name);
        markChanged();
    }

    public void removeFriend(UUID id) {
        friends.remove(id);
        friendNames.remove(id);
        pinSessions.remove(id);
        markChanged();
    }

    /** Remove by name: drops name-only entries and any UUID entry whose
     * stored display name matches case-insensitively. */
    public void removeFriend(String name) {
        if (name == null) return;
        String lower = name.toLowerCase(Locale.ROOT);
        nameFriends.remove(lower);
        nameFriendDisplay.remove(lower);
        Iterator<UUID> it = friends.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            String n = friendNames.get(id);
            if (n != null && n.equalsIgnoreCase(name)) {
                it.remove();
                friendNames.remove(id);
                pinSessions.remove(id);
            }
        }
        markChanged();
    }

    /** Sorted display names of every friend (UUID and name-only entries). */
    public List<String> getFriendNameList() {
        List<String> out = new ArrayList<String>();
        for (UUID id : friends) {
            String n = friendNames.get(id);
            out.add(n != null ? n : id.toString().substring(0, 8));
        }
        for (String lower : nameFriends) {
            String display = nameFriendDisplay.get(lower);
            out.add(display != null ? display : lower);
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public String getFriendNames() {
        List<String> names = getFriendNameList();
        if (names.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(n);
        }
        return sb.toString();
    }

    /** Player names must look like Minecraft usernames. */
    public static boolean isValidPlayerName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    /**
     * Resolve a player name to a UUID for friend adds.
     * - Online players: their live GameProfile UUID.
     * - Offline-mode servers: the deterministic offline UUID, derived exactly
     *   the way vanilla does (so it matches when the player joins).
     * - Online-mode servers: a Mojang profile API lookup (cached, short
     *   timeout); null when the lookup fails, in which case the caller
     *   should fall back to a name-only friend entry.
     *
     * WARNING: the online-mode Mojang lookup does blocking network I/O.
     * Never call this on the server thread; use {@link #resolveFriendUUIDFast}
     * there and {@link #lookupMojangUUID} off-thread instead.
     */
    public static UUID resolveFriendUUID(MinecraftServer server, String name) {
        UUID fast = resolveFriendUUIDFast(server, name);
        if (fast != null) return fast;
        if (server == null || !server.isServerInOnlineMode()) return null;
        return lookupMojangUUID(name);
    }

    /**
     * Server-thread-safe subset of {@link #resolveFriendUUID}: resolves
     * online players and offline-mode UUIDs instantly. Returns null when
     * the name would require a Mojang API lookup (online-mode server,
     * player not currently online) — the caller must then run
     * {@link #lookupMojangUUID} off the server thread.
     */
    public static UUID resolveFriendUUIDFast(MinecraftServer server, String name) {
        if (server == null || name == null) return null;
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        if (online != null) return online.getUniqueID();
        if (!server.isServerInOnlineMode()) {
            return offlineUUID(name);
        }
        return null;
    }

    /** Vanilla's offline-mode UUID derivation. */
    public static UUID offlineUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static final Map<String, UUID> MOJANG_CACHE =
            new LinkedHashMap<String, UUID>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, UUID> eldest) {
                    return size() > 128;
                }
            };
    private static final Set<String> MOJANG_MISS = new HashSet<String>();

    /** Blocking Mojang lookup. Call off the server thread (see AddFriendMessage). */
    public static UUID lookupMojangUUID(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        synchronized (MOJANG_CACHE) {
            if (MOJANG_CACHE.containsKey(key)) return MOJANG_CACHE.get(key);
            if (MOJANG_MISS.contains(key)) return null;
        }
        UUID found = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + key);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "SecureCC/1.1.0");
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                JsonObject obj = new JsonParser().parse(sb.toString()).getAsJsonObject();
                String id = obj.get("id").getAsString();
                if (id != null && id.length() == 32) {
                    found = new UUID(Long.parseUnsignedLong(id.substring(0, 16), 16),
                            Long.parseUnsignedLong(id.substring(16, 32), 16));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        synchronized (MOJANG_CACHE) {
            if (found != null) {
                MOJANG_CACHE.put(key, found);
            } else {
                if (MOJANG_MISS.size() > 512) MOJANG_MISS.clear();
                MOJANG_MISS.add(key);
            }
        }
        return found;
    }

    // ---------- PIN (salted SHA-256, throttled) ----------

    public boolean hasPin() {
        return pinHash != null;
    }

    public void setPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            pinHash = null;
            pinSalt = null;
        } else {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            pinSalt = hexEncode(salt);
            pinHash = sha256Hex(salt, pin);
        }
        pinFails = 0;
        pinLockoutUntil = 0;
        markChanged();
    }

    /** Constant-time comparison of a candidate PIN against the stored hash. */
    public boolean checkPin(String candidate) {
        if (pinHash == null || pinSalt == null || candidate == null) return false;
        String attempt = sha256Hex(hexDecode(pinSalt), candidate);
        return MessageDigest.isEqual(pinHash.getBytes(StandardCharsets.UTF_8),
                attempt.getBytes(StandardCharsets.UTF_8));
    }

    /** True while a brute-force lockout is in effect. */
    public boolean isPinLockedOut() {
        if (pinLockoutUntil == 0) return false;
        if (System.currentTimeMillis() >= pinLockoutUntil) {
            pinLockoutUntil = 0;
            pinFails = 0;
            return false;
        }
        return true;
    }

    /** Whole seconds remaining on the current lockout (0 when not locked). */
    public long getPinLockoutRemainingSeconds() {
        if (!isPinLockedOut()) return 0;
        return Math.max(1, (pinLockoutUntil - System.currentTimeMillis() + 999) / 1000);
    }

    public void recordPinAttempt(boolean success) {
        if (success) {
            pinFails = 0;
            pinLockoutUntil = 0;
            return;
        }
        pinFails++;
        if (pinFails >= Math.max(1, SecureConfig.pinMaxAttempts)) {
            long secs = SecureConfig.pinLockoutSeconds;
            if (secs > 0) {
                pinLockoutUntil = System.currentTimeMillis() + secs * 1000L;
            }
            pinFails = 0;
        }
    }

    private static String sha256Hex(byte[] salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pin.getBytes(StandardCharsets.UTF_8));
            return hexEncode(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                    + Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return out;
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

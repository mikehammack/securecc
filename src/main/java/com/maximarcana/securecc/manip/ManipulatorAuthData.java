package com.maximarcana.securecc.manip;

import com.maximarcana.securecc.SecureAccess;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Owner / policy / friend / PIN state for every secure manipulator.
 * The vanilla manipulator tile is final and cannot carry the state, so it
 * lives here, keyed by dimension + block position.
 */
public class ManipulatorAuthData extends WorldSavedData {
    public static final String NAME = "securecc_manipulators";

    private final Map<String, SecureAccess> auths = new HashMap<String, SecureAccess>();

    public ManipulatorAuthData() {
        super(NAME);
    }

    public ManipulatorAuthData(String name) {
        super(name);
    }

    private static String key(World world, BlockPos pos) {
        return world.provider.getDimensionType().getId() + ":" + pos.toLong();
    }

    /** Returns the data, or null on the client side. */
    public static ManipulatorAuthData get(World world) {
        if (world == null || world.isRemote) return null;
        MapStorage storage = world.getMapStorage();
        if (storage == null) return null;
        ManipulatorAuthData data =
                (ManipulatorAuthData) storage.getOrLoadData(ManipulatorAuthData.class, NAME);
        if (data == null) {
            data = new ManipulatorAuthData();
            storage.setData(NAME, data);
        }
        return data;
    }

    public SecureAccess get(BlockPos pos, World world) {
        return auths.get(key(world, pos));
    }

    public SecureAccess getOrCreate(BlockPos pos, World world) {
        String k = key(world, pos);
        SecureAccess access = auths.get(k);
        if (access == null) {
            access = new SecureAccess();
            access.setDirtyMark(this::markDirty);
            auths.put(k, access);
        }
        return access;
    }

    public void remove(BlockPos pos, World world) {
        if (auths.remove(key(world, pos)) != null) markDirty();
    }

    public Collection<SecureAccess> all() {
        return auths.values();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        auths.clear();
        NBTTagList list = tag.getTagList("auths", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            SecureAccess access = new SecureAccess();
            access.setDirtyMark(this::markDirty);
            access.readFromNBT(entry.getCompoundTag("access"));
            auths.put(entry.getString("key"), access);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, SecureAccess> e : auths.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("key", e.getKey());
            entry.setTag("access", e.getValue().writeToNBT(new NBTTagCompound()));
            list.appendTag(entry);
        }
        tag.setTag("auths", list);
        return tag;
    }
}

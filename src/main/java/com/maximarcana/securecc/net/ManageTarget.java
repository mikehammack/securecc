package com.maximarcana.securecc.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;

/**
 * Where a management GUI is bound: either a secure block (position) or a
 * worn secure neural interface (entity id). Carries the dimension so the
 * client cache can key entries unambiguously. Serialized into every
 * management packet.
 */
public final class ManageTarget {
    public final int dimension;
    public final boolean isEntity;
    public final BlockPos pos; // valid when !isEntity
    public final int entityId;  // valid when isEntity

    private ManageTarget(int dimension, BlockPos pos, int entityId) {
        this.dimension = dimension;
        this.isEntity = pos == null;
        this.pos = pos;
        this.entityId = entityId;
    }

    public static ManageTarget forBlock(int dimension, BlockPos pos) {
        if (pos == null) throw new IllegalArgumentException("pos");
        return new ManageTarget(dimension, pos, -1);
    }

    public static ManageTarget forEntity(int dimension, int entityId) {
        return new ManageTarget(dimension, null, entityId);
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeBoolean(isEntity);
        if (isEntity) {
            buf.writeInt(entityId);
        } else {
            buf.writeLong(pos.toLong());
        }
    }

    public static ManageTarget fromBytes(ByteBuf buf) {
        int dimension = buf.readInt();
        boolean isEntity = buf.readBoolean();
        if (isEntity) {
            return forEntity(dimension, buf.readInt());
        }
        return forBlock(dimension, BlockPos.fromLong(buf.readLong()));
    }

    /** Cache key shared by the client cache and GUI refresh matching. */
    public String cacheKey() {
        return dimension + (isEntity ? ":e:" + entityId : ":b:" + pos.toLong());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ManageTarget)) return false;
        ManageTarget other = (ManageTarget) o;
        return dimension == other.dimension && isEntity == other.isEntity
                && entityId == other.entityId
                && (pos == null ? other.pos == null : pos.equals(other.pos));
    }

    @Override
    public int hashCode() {
        int h = dimension * 31 + (isEntity ? 1 : 0);
        h = h * 31 + entityId;
        return h * 31 + (pos == null ? 0 : pos.hashCode());
    }

    @Override
    public String toString() {
        return "ManageTarget[" + cacheKey() + "]";
    }
}

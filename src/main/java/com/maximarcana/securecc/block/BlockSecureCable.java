package com.maximarcana.securecc.block;

import com.maximarcana.securecc.ModBlocks;
import com.maximarcana.securecc.SecureAccess;
import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureConfig;
import com.maximarcana.securecc.tile.TileSecureCable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Secure networking cable.
 *
 * A thin cable block that links secure modems into a private wired network.
 * Cables connect to other secure cables and to secure modems only — never
 * to computers directly and never to vanilla cables/modems, so the line
 * cannot be tapped with standard equipment. The modem is the peripheral;
 * the cable is just the wire.
 *
 * Ownership is claimed on placement; only the owner (or an op) can break it.
 */
public class BlockSecureCable extends BlockContainer {
    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool WEST = PropertyBool.create("west");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool UP = PropertyBool.create("up");
    public static final PropertyBool DOWN = PropertyBool.create("down");

    public BlockSecureCable() {
        super(Material.IRON);
        setRegistryName(SecureCC.MODID, "secure_cable");
        setTranslationKey(SecureCC.MODID + ".secure_cable");
        setHardness(SecureConfig.blockHardness);
        setResistance(SecureConfig.blockResistance);
        setDefaultState(this.blockState.getBaseState()
                .withProperty(NORTH, false)
                .withProperty(SOUTH, false)
                .withProperty(WEST, false)
                .withProperty(EAST, false)
                .withProperty(UP, false)
                .withProperty(DOWN, false));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSecureCable();
    }

    // ---------- Blockstate ----------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(NORTH, canConnectTo(world, pos.north()))
                .withProperty(SOUTH, canConnectTo(world, pos.south()))
                .withProperty(WEST, canConnectTo(world, pos.west()))
                .withProperty(EAST, canConnectTo(world, pos.east()))
                .withProperty(UP, canConnectTo(world, pos.up()))
                .withProperty(DOWN, canConnectTo(world, pos.down()));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    /**
     * Cables connect to other secure cables and to secure modems only.
     * They deliberately do NOT connect to computers or vanilla wiring.
     */
    private boolean canConnectTo(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        // Use registry-name comparison to avoid static-init ordering issues
        // during ModBlocks initialization.
        if (block == ModBlocks.SECURE_CABLE || block == ModBlocks.SECURE_MODEM) {
            return true;
        }
        // Fallback: compare registry names (handles cases where ModBlocks
        // statics are not yet assigned).
        if (block.getRegistryName() != null) {
            String name = block.getRegistryName().toString();
            return "securecc:secure_cable".equals(name) || "securecc:secure_modem".equals(name);
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        // Force a render update so connection arms refresh.
        if (!world.isRemote) {
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    // ---------- Placement / breaking ----------

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                               EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSecureCable) {
                TileSecureCable tile = (TileSecureCable) te;
                SecureAccess access = tile.getSecureAccess();
                if (!access.hasOwner()
                        && !access.restoreOwnerFromStack(stack)) {
                    access.setOwner((EntityPlayer) placer);
                }
            }
        }
        // Update neighbours so their arms connect to us.
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    // Break protection is handled centrally in SecureEventHandler.onBlockBreak
    // (BlockEvent.BreakEvent), which covers all ISecureTile blocks.

    @Override
    public boolean onBlockActivated(World world, BlockPos pos,
                                   IBlockState state, EntityPlayer player,
                                   EnumHand hand, EnumFacing facing,
                                   float hitX, float hitY, float hitZ) {
        return false;
    }

    // ---------- Rendering ----------

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source,
                                       BlockPos pos) {
        IBlockState actual = getActualState(state, source, pos);
        double minX = 0.375D, minY = 0.375D, minZ = 0.375D;
        double maxX = 0.625D, maxY = 0.625D, maxZ = 0.625D;
        if (actual.getValue(WEST)) minX = 0.0D;
        if (actual.getValue(EAST)) maxX = 1.0D;
        if (actual.getValue(DOWN)) minY = 0.0D;
        if (actual.getValue(UP)) maxY = 1.0D;
        if (actual.getValue(NORTH)) minZ = 0.0D;
        if (actual.getValue(SOUTH)) maxZ = 1.0D;
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox,
                                      java.util.List<AxisAlignedBB> collidingBoxes,
                                      net.minecraft.entity.Entity entityIn,
                                      boolean isActualState) {
        // Use the connection-aware bounding box for collision as well.
        AxisAlignedBB box = getBoundingBox(state, world, pos);
        if (box.intersects(entityBox)) {
            collidingBoxes.add(box);
        }
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullBlock(IBlockState state) {
        return false;
    }
}

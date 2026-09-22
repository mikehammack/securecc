package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureAccess;
import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureConfig;
import com.maximarcana.securecc.manip.ManipulatorAuthData;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.squiddev.plethora.gameplay.modules.BlockManipulator;
import org.squiddev.plethora.gameplay.modules.ManipulatorType;
import org.squiddev.plethora.gameplay.modules.TileManipulator;

import net.minecraft.util.BlockRenderLayer;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.util.EnumBlockRenderType;

/**
 * Diamond-tier manipulator. Mirrors the Mark II's looks and peripheral
 * behaviour exactly, but tracks an owner and gates the module GUI.
 *
 * Integration notes:
 * - Plethora's BlockManipulator is final and its BlockBase hardcodes the
 *   plethora registry domain, so this extends BlockContainer directly and
 *   replicates BlockBase's small surface (tile lookup, GUI activation,
 *   neighbour forwarding). It creates genuine TileManipulator tiles
 *   (mark 2) so every module, recipe and rendering path works.
 * - getPeripheral() delegates to an unregistered BlockManipulator instance:
 *   Plethora's peripheral builder only reads the tile, never the block, so
 *   the exposed peripheral is byte-for-byte identical to a Mark II's.
 */
public class BlockSecureManipulator extends BlockContainer
        implements IPeripheralProvider {
    /** Unregistered vessel: getPeripheral never touches `this`, so one
     * shared instance serves every secure manipulator in the world. */
    private static final BlockManipulator DELEGATE = new BlockManipulator();

    public static final PropertyDirection FACING =
            PropertyDirection.create("facing",
                    java.util.Arrays.asList(EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST));

    public BlockSecureManipulator() {
        super(Material.IRON);
        setRegistryName(SecureCC.MODID, "secure_manipulator");
        setTranslationKey(SecureCC.MODID + ".secure_manipulator");
        setHardness(SecureConfig.blockHardness);
        setResistance(SecureConfig.blockResistance);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
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
    @SuppressWarnings("deprecation")
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                           float hitX, float hitY, float hitZ, int meta,
                                           EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileManipulator(ManipulatorType.MARK_2);
    }

    /** Mirrors BlockBase.getTile: the manipulator tile at this position, if any. */
    public TileManipulator getTile(IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileManipulator ? (TileManipulator) te : null;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                               EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (placer instanceof EntityPlayer) {
            TileManipulator tile = getTile(world, pos);
            if (tile != null) {
                tile.setOwningProfile(((EntityPlayer) placer).getGameProfile());
            }
            if (!world.isRemote) {
                ManipulatorAuthData data = ManipulatorAuthData.get(world);
                if (data != null) {
                    SecureAccess access = data.getOrCreate(pos, world);
                    if (!access.hasOwner()) access.setOwner((EntityPlayer) placer);
                }
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                   EntityPlayer player, EnumHand hand, EnumFacing facing,
                                   float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            ManipulatorAuthData data = ManipulatorAuthData.get(world);
            SecureAccess access = data == null ? null : data.get(pos, world);
            if (access != null && !access.canUseTerminal(player, world)) {
                player.sendMessage(new TextComponentString(
                        "\u00a7cThis secure manipulator belongs to " + access.getOwnerName()
                                + ". You are not authorised to open it."));
                return true;
            }
        }
        // Replicates BlockBase.onBlockActivated (the Plethora module GUI).
        TileManipulator tile = getTile(world, pos);
        return tile != null && tile.onActivated(player, hand, facing, new Vec3d(hitX, hitY, hitZ));
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, block, fromPos);
        TileManipulator tile = getTile(world, pos);
        if (tile != null) tile.onNeighborChanged();
    }

    /**
     * Clean up the secure auth entry when the block is actually removed.
     * Break *permission* is enforced centrally in SecureEventHandler's
     * BreakEvent handler (this parent does not expose removedByPlayer).
     */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            ManipulatorAuthData data = ManipulatorAuthData.get(world);
            if (data != null) data.remove(pos, world);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public IPeripheral getPeripheral(World world, BlockPos pos, EnumFacing side) {
        return DELEGATE.getPeripheral(world, pos, side);
    }
}

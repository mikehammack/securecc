package com.maximarcana.securecc.block;

import com.maximarcana.securecc.SecureCC;
import com.maximarcana.securecc.SecureConfig;
import com.maximarcana.securecc.tile.TileSecureMonitor;
import dan200.computercraft.shared.common.BlockGeneric;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.common.BlockPeripheral;
import dan200.computercraft.shared.peripheral.common.BlockPeripheralVariant;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import dan200.computercraft.shared.util.DirectionUtil;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;

/**
 * Diamond-tier monitor. Carries the VARIANT property pinned to
 * {@code advanced_monitor} so the vanilla tile/controller logic treats it
 * as an advanced monitor (merging, rendering, resolution).
 */
public class BlockSecureMonitor extends BlockGeneric {
    public static final PropertyDirection FACING = PropertyDirection.create("facing",
            Arrays.asList(EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST));

    public BlockSecureMonitor() {
        super(Material.ROCK);
        setRegistryName(SecureCC.MODID, "secure_monitor");
        setTranslationKey(SecureCC.MODID + ".secure_monitor");
        setHardness(SecureConfig.blockHardness);
        setResistance(SecureConfig.blockResistance);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(BlockPeripheral.VARIANT, BlockPeripheralVariant.AdvancedMonitor));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, BlockPeripheral.VARIANT);
    }

    /**
     * Must match {@link BlockPeripheral#getRenderLayer()}: the monitor frame
     * texture has a transparent screen cutout. In the default SOLID layer the
     * cutout is drawn opaque and z-fights with the TESR screen quad, producing
     * angle-dependent diagonal "glare" streaks. CUTOUT discards the cutout so
     * the screen shows through cleanly.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
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

    /**
     * Replicates BlockPeripheral's merge-state logic so multi-block monitor
     * walls render with the correct merged model. The world state keeps the
     * plain {@code advanced_monitor} variant; the merged variant
     * (advanced_monitor_l, _lrud, ...) is computed here for rendering, from
     * the tile's position within its monitor wall.
     */
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileMonitor)) return state;
        TileMonitor monitor = (TileMonitor) te;
        EnumFacing direction = monitor.getDirection();
        EnumFacing front = monitor.getFront();
        int xIndex = monitor.getXIndex();
        int yIndex = monitor.getYIndex();
        int width = monitor.getWidth();
        int height = monitor.getHeight();

        BlockPeripheralVariant base;
        if (front == EnumFacing.UP) {
            base = BlockPeripheralVariant.AdvancedMonitorUp;
        } else if (front == EnumFacing.DOWN) {
            base = BlockPeripheralVariant.AdvancedMonitorDown;
        } else {
            base = BlockPeripheralVariant.AdvancedMonitor;
        }

        int offset;
        if (width == 1 && height == 1) {
            offset = 0;
        } else if (height == 1) {
            offset = xIndex == 0 ? 1 : xIndex == width - 1 ? 3 : 2;
        } else if (width == 1) {
            offset = yIndex == 0 ? 6 : yIndex == height - 1 ? 4 : 5;
        } else {
            offset = xIndex == 0 ? 7 : xIndex == width - 1 ? 9 : 8;
            if (yIndex == 0) {
                offset += 6;
            } else if (yIndex < height - 1) {
                offset += 3;
            }
        }

        BlockPeripheralVariant merged =
                BlockPeripheralVariant.values()[base.ordinal() + offset];
        // getDirection() is horizontal in practice (vanilla does the same
        // withProperty), but never let a bad value crash rendering.
        EnumFacing facing = direction.getAxis().isHorizontal() ? direction : state.getValue(FACING);
        return state.withProperty(FACING, facing)
                .withProperty(BlockPeripheral.VARIANT, merged);
    }

    @Override
    protected TileGeneric createTile(IBlockState state) {
        return new TileSecureMonitor();
    }

    @Override
    protected TileGeneric createTile(int meta) {
        return new TileSecureMonitor();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                               EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        TileEntity te = world.getTileEntity(pos);
        // Replicates BlockPeripheral.onBlockPlacedBy's monitor branch:
        // without contract/expand the multi-block grid never forms.
        if (te instanceof TileMonitor) {
            TileMonitor monitor = (TileMonitor) te;
            int dir = DirectionUtil.fromEntityRot(placer).getIndex();
            if (placer.rotationPitch > 66.5f) {
                dir += 12;
            } else if (placer.rotationPitch < -66.5f) {
                dir += 6;
            }
            if (world.isRemote) {
                monitor.setDir(dir);
            } else {
                monitor.contractNeighbours();
                monitor.setDir(dir);
                monitor.contract();
                monitor.expand();
            }
        }
        if (!world.isRemote && placer instanceof EntityPlayer && te instanceof TileSecureMonitor) {
            TileSecureMonitor tile = (TileSecureMonitor) te;
            if (!tile.hasOwner()) tile.setOwner((EntityPlayer) placer);
        }
    }

    /**
     * Tile creation: the real BlockGeneric implements
     * ITileEntityProvider.createNewTileEntity as FINAL under its SRG name
     * and delegates to createTile(int), which this class overrides above.
     * This class must NOT declare its own createNewTileEntity — javac is
     * satisfied via a compile-only stub (see src/stubs), and at runtime
     * the inherited final method is used. (If SpecialSource ever renamed
     * an override here to func_149915_a it would collide with that final
     * method and the class would fail verification.)
     */
}

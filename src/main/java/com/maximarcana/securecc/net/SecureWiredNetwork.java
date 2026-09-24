package com.maximarcana.securecc.net;

import com.maximarcana.securecc.tile.TileSecureCable;
import com.maximarcana.securecc.tile.TileSecureModem;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.api.peripheral.IPeripheralTile;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wired network helper: finds secure modems linked by secure cables.
 *
 * Topology: modems are endpoints, cables are wire. A modem transmits
 * through adjacent cables; BFS floods through cable tiles and collects
 * every reachable modem. Cables never talk to computers directly.
 */
public final class SecureWiredNetwork {
    private SecureWiredNetwork() {}

    /**
     * Find all secure modems reachable from the given modem via cables.
     * Must run on the server thread.
     */
    public static Set<TileSecureModem> findConnectedModems(World world, BlockPos modemPos) {
        Set<TileSecureModem> modems = new HashSet<TileSecureModem>();
        Set<BlockPos> visited = new HashSet<BlockPos>();
        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();

        // Seed with cables adjacent to the sending modem.
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighbor = modemPos.offset(facing);
            TileEntity te = world.getTileEntity(neighbor);
            if (te instanceof TileSecureCable && !((TileSecureCable) te).isInvalid()) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileSecureCable)) continue;
            TileSecureCable cable = (TileSecureCable) te;
            if (cable.isInvalid()) continue;

            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos next = pos.offset(facing);
                if (!visited.add(next)) continue;
                TileEntity nextTe = world.getTileEntity(next);
                if (nextTe instanceof TileSecureCable) {
                    if (!((TileSecureCable) nextTe).isInvalid()) {
                        queue.add(next);
                    }
                } else if (nextTe instanceof TileSecureModem) {
                    TileSecureModem modem = (TileSecureModem) nextTe;
                    if (!modem.isInvalid()) {
                        modems.add(modem);
                    }
                    // Do not traverse through modems.
                }
            }
        }
        return modems;
    }

    /**
     * Deliver an already-encrypted packet to a set of modems (normally the
     * result of {@link #findConnectedModems}). Callers compute the target
     * set once and reuse it, so the cable BFS never runs twice per send.
     * Receivers decrypt with their own owner key and drop packets that
     * fail (different owner). Must run on the server thread.
     */
    public static void deliverWired(int channel, String encrypted,
                                   Set<TileSecureModem> modems) {
        for (TileSecureModem modem : modems) {
            modem.receiveWired(channel, encrypted);
        }
    }

    // ---------- Remote peripherals (vanilla wired-modem style) ----------

    /**
     * Find peripherals attached to the secure wired network, reachable
     * through the modem at {@code modemPos}. Mirrors the vanilla wired
     * modem: every peripheral touching any cable or modem on the network
     * gets a network name like {@code "monitor_0"}.
     *
     * <ul>
     *   <li>Cables and modems are wire, never peripherals.</li>
     *   <li>Computers and turtles (vanilla or secure) are endpoints, not
     *       shareable peripherals: nothing on the wire may remotely drive
     *       a computer.</li>
     *   <li>Names are deterministic: network nodes are visited in BFS
     *       order (origin modem, cables, then other modems by position)
     *       and each node's sides in {@link EnumFacing} order; each
     *       peripheral type gets its own counter.</li>
     * </ul>
     * Must run on the server thread.
     */
    public static Map<String, IPeripheral> findRemotePeripherals(
            World world, BlockPos modemPos) {
        Map<String, IPeripheral> out = new LinkedHashMap<String, IPeripheral>();
        Map<String, Integer> counters = new HashMap<String, Integer>();
        Set<BlockPos> seenTiles = new HashSet<BlockPos>();
        for (BlockPos node : findNetworkNodes(world, modemPos)) {
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos neighborPos = node.offset(facing);
                if (!seenTiles.add(neighborPos)) continue;
                TileEntity te = world.getTileEntity(neighborPos);
                if (te == null || te.isInvalid()) continue;
                if (te instanceof TileSecureCable || te instanceof TileSecureModem) {
                    continue; // wire, not a peripheral
                }
                if (te instanceof TileComputerBase) {
                    continue; // computers/turtles are endpoints, not peripherals
                }
                IPeripheral p = peripheralFrom(world, neighborPos,
                        facing.getOpposite(), te);
                if (p == null) continue;
                String type = p.getType();
                if (type == null || "secure_modem".equals(type)) continue;
                int index = counters.containsKey(type) ? counters.get(type) : 0;
                counters.put(type, index + 1);
                out.put(type + "_" + index, p);
            }
        }
        return out;
    }

    /**
     * Network node positions in deterministic scan order: the origin modem,
     * then cables in BFS order, then the other reachable modems sorted by
     * position. Modems are endpoints: the flood never passes through them.
     */
    private static List<BlockPos> findNetworkNodes(World world, BlockPos modemPos) {
        List<BlockPos> nodes = new ArrayList<BlockPos>();
        Set<BlockPos> visited = new HashSet<BlockPos>();
        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        Set<BlockPos> otherModems = new LinkedHashSet<BlockPos>();

        visited.add(modemPos);
        TileEntity origin = world.getTileEntity(modemPos);
        if (!(origin instanceof TileSecureModem) || origin.isInvalid()) {
            return nodes;
        }
        nodes.add(modemPos);

        // Seed with cables adjacent to the origin modem.
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighbor = modemPos.offset(facing);
            if (!visited.add(neighbor)) continue;
            TileEntity te = world.getTileEntity(neighbor);
            if (te instanceof TileSecureCable && !te.isInvalid()) {
                queue.add(neighbor);
            } else if (te instanceof TileSecureModem && !te.isInvalid()) {
                otherModems.add(neighbor);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileSecureCable) || te.isInvalid()) continue;
            nodes.add(pos);
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos next = pos.offset(facing);
                if (!visited.add(next)) continue;
                TileEntity nextTe = world.getTileEntity(next);
                if (nextTe == null || nextTe.isInvalid()) continue;
                if (nextTe instanceof TileSecureCable) {
                    queue.add(next);
                } else if (nextTe instanceof TileSecureModem) {
                    otherModems.add(next);
                }
            }
        }

        // Deterministic modem order: sort by coordinates.
        List<BlockPos> sortedModems = new ArrayList<BlockPos>(otherModems);
        sortedModems.sort((a, b) -> {
            int c = Integer.compare(a.getX(), b.getX());
            if (c != 0) return c;
            c = Integer.compare(a.getY(), b.getY());
            if (c != 0) return c;
            return Integer.compare(a.getZ(), b.getZ());
        });
        nodes.addAll(sortedModems);
        return nodes;
    }

    /**
     * Get the peripheral a tile (or its block's provider) offers on the
     * given side. Mirrors CC's default provider; a broken third-party
     * provider degrades to "no peripheral" instead of breaking the scan.
     */
    private static IPeripheral peripheralFrom(World world, BlockPos pos,
                                             EnumFacing side, TileEntity te) {
        try {
            if (te instanceof IPeripheralTile) {
                IPeripheral p = ((IPeripheralTile) te).getPeripheral(side);
                if (p != null) return p;
            }
            // Some mods implement IPeripheral directly on the tile entity
            // instead of going through IPeripheralTile or a block-level
            // provider.
            if (te instanceof IPeripheral) {
                return (IPeripheral) te;
            }
            Block block = world.getBlockState(pos).getBlock();
            if (block instanceof IPeripheralProvider) {
                return ((IPeripheralProvider) block).getPeripheral(world, pos, side);
            }
        } catch (Exception e) {
            // ignore a misbehaving provider
        }
        return null;
    }
}

package com.maximarcana.securecc.peripheral;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IWorkMonitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The "secure_modem" Lua peripheral, served by both the secure cable
 * (wired) and the secure modem block (wireless).
 *
 * Lua API (mirrors the vanilla modem):
 *   open(channel), isOpen(channel), close(channel), closeAll(),
 *   transmit(channel, replyChannel, payload), isWireless(),
 *   getNamesRemote(), isPresentRemote(name), getTypeRemote(name),
 *   getMethodsRemote(name), callRemote(name, method, ...)
 *
 * Events: "modem_message" (side, channel, replyChannel, message, distance)
 *
 * The concrete transport (cable flood vs. wireless broadcast) is supplied
 * by the {@link Transport} installed by the owning tile.
 */
public class SecureModemPeripheral implements IPeripheral {
    /** Outbound transport supplied by the tile (cable or wireless). */
    public interface Transport {
        /**
         * Send a plaintext payload to the network. The tile encrypts it
         * with the owner-derived key before it leaves the device.
         */
        void transmit(int channel, int replyChannel, String plaintext);
        /** True for the wireless modem block, false for cable. */
        boolean isWireless();
        /**
         * Scan the secure wired network for remotely reachable peripherals,
         * vanilla wired-modem style. Returns an ordered map of network name
         * (e.g. "monitor_0") to peripheral, rebuilt fresh on every call.
         * Must run on the server thread.
         */
        Map<String, IPeripheral> scanRemotePeripherals();
    }

    private final Transport transport;
    private final Set<IComputerAccess> computers = new HashSet<IComputerAccess>();
    private final Set<Integer> openChannels = new HashSet<Integer>();
    /**
     * Stable attachments to remote peripherals, keyed by computer ID then
     * network name. Some peripherals (notably Plethora's) only accept
     * method calls from a computer access they have seen via attach(), so
     * callRemote must present the same IComputerAccess instance every
     * time instead of a fresh wrapper per call.
     */
    private final Map<Integer, Map<String, AttachedRemote>> attachedRemotes =
            new HashMap<Integer, Map<String, AttachedRemote>>();

    public SecureModemPeripheral(Transport transport) {
        this.transport = transport;
    }

    // ---------- IPeripheral ----------

    @Override
    public String getType() {
        return "secure_modem";
    }

    @Override
    public String[] getMethodNames() {
        return new String[]{
                "open", "isOpen", "close", "closeAll", "transmit", "isWireless",
                "getNamesRemote", "isPresentRemote", "getTypeRemote",
                "getMethodsRemote", "callRemote",
        };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context,
                              int method, Object[] args)
            throws LuaException, InterruptedException {
        switch (method) {
            case 0: { // open(channel)
                int channel = parseChannel(args, 0);
                synchronized (openChannels) {
                    openChannels.add(channel);
                }
                return null;
            }
            case 1: { // isOpen(channel)
                int channel = parseChannel(args, 0);
                synchronized (openChannels) {
                    return new Object[]{openChannels.contains(channel)};
                }
            }
            case 2: { // close(channel)
                int channel = parseChannel(args, 0);
                synchronized (openChannels) {
                    openChannels.remove(channel);
                }
                return null;
            }
            case 3: { // closeAll()
                synchronized (openChannels) {
                    openChannels.clear();
                }
                return null;
            }
            case 4: { // transmit(channel, replyChannel, payload)
                int channel = parseChannel(args, 0);
                int replyChannel = parseChannel(args, 1);
                if (args.length < 3) throw new LuaException("Expected 3 arguments");
                String payload = coerceToString(args[2]);
                // The tile encrypts with the owner key before sending.
                transport.transmit(channel, replyChannel, payload);
                return null;
            }
            case 5: { // isWireless()
                return new Object[]{transport.isWireless()};
            }
            case 6: { // getNamesRemote()
                Set<String> names = remoteScan().keySet();
                return new Object[]{names.toArray(new String[names.size()])};
            }
            case 7: { // isPresentRemote(name)
                String name = parseRemoteName(args);
                return new Object[]{remoteScan().containsKey(name)};
            }
            case 8: { // getTypeRemote(name)
                return new Object[]{requireRemote(args).getType()};
            }
            case 9: { // getMethodsRemote(name)
                return new Object[]{requireRemote(args).getMethodNames()};
            }
            case 10: { // callRemote(name, method, ...)
                if (args.length < 2) throw new LuaException("Expected at least 2 arguments");
                if (!(args[0] instanceof String)) {
                    throw new LuaException("Expected string peripheral name");
                }
                if (!(args[1] instanceof String)) {
                    throw new LuaException("Expected string method name");
                }
                String name = (String) args[0];
                String methodName = (String) args[1];
                IPeripheral target = remoteScan().get(name);
                if (target == null) {
                    throw new LuaException("No such peripheral: " + name);
                }
                int index = Arrays.asList(target.getMethodNames()).indexOf(methodName);
                if (index < 0) {
                    throw new LuaException("No such method " + methodName
                            + " on peripheral " + name);
                }
                Object[] rest = Arrays.copyOfRange(args, 2, args.length);
                // Present a stable, attached access to the remote peripheral.
                // Peripherals like Plethora's reject calls from an access
                // they have not seen via attach() ("Not attached to this
                // computer"), and a fresh wrapper per call never matches.
                return target.callMethod(attachedAccess(computer, name, target),
                        context, index, rest);
            }
            default:
                return null;
        }
    }

    // ---------- Remote peripherals (wired network) ----------

    /**
     * Scan the secure wired network for remotely reachable peripherals.
     * Rebuilt fresh on every call so topology changes are picked up
     * immediately. A broken transport scan degrades to an empty network,
     * never to a Lua error.
     */
    private Map<String, IPeripheral> remoteScan() {
        try {
            Map<String, IPeripheral> found = transport.scanRemotePeripherals();
            return found == null
                    ? Collections.<String, IPeripheral>emptyMap() : found;
        } catch (Exception e) {
            return Collections.<String, IPeripheral>emptyMap();
        }
    }

    private static String parseRemoteName(Object[] args) throws LuaException {
        if (args.length < 1 || !(args[0] instanceof String)) {
            throw new LuaException("Expected string peripheral name");
        }
        return (String) args[0];
    }

    private IPeripheral requireRemote(Object[] args) throws LuaException {
        String name = parseRemoteName(args);
        IPeripheral target = remoteScan().get(name);
        if (target == null) {
            throw new LuaException("No such peripheral: " + name);
        }
        return target;
    }

    /**
     * One live attachment to a remote peripheral: the underlying peripheral
     * instance we attached to, plus the stable access wrapper we present
     * for every callRemote so attach-gated peripherals (Plethora) accept it.
     */
    private static final class AttachedRemote {
        final IPeripheral target;
        final RemoteAccess access;

        AttachedRemote(IPeripheral target, RemoteAccess access) {
            this.target = target;
            this.access = access;
        }
    }

    /**
     * Return the stable attached access for this computer and remote name,
     * attaching to the underlying peripheral on first use. Keyed by
     * computer ID (stable across calls) rather than the access instance.
     * Attach is best-effort: peripherals that do not care about attach keep
     * working exactly as before.
     */
    private RemoteAccess attachedAccess(IComputerAccess computer, String name,
                                       IPeripheral target) {
        int id = computer.getID();
        synchronized (attachedRemotes) {
            Map<String, AttachedRemote> byName = attachedRemotes.get(id);
            if (byName == null) {
                byName = new HashMap<String, AttachedRemote>();
                attachedRemotes.put(id, byName);
            }
            AttachedRemote current = byName.get(name);
            if (current == null || current.target != target) {
                if (current != null) {
                    try {
                        current.target.detach(current.access);
                    } catch (Exception ignored) {
                        // already gone; nothing to clean up
                    }
                }
                RemoteAccess access = new RemoteAccess(computer, name);
                try {
                    target.attach(access);
                } catch (Exception ignored) {
                    // attach-gating is optional; the call below decides
                }
                current = new AttachedRemote(target, access);
                byName.put(name, current);
            }
            return current.access;
        }
    }

    /**
     * Presents the calling computer's access to a remote peripheral with
     * the network name as the attachment name, mirroring vanilla's wired
     * modem behaviour.
     */
    private static final class RemoteAccess implements IComputerAccess {
        private final IComputerAccess parent;
        private final String name;

        RemoteAccess(IComputerAccess parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        @Override
        public String mount(String desiredLocation, IMount mount, String driveName) {
            return parent.mount(desiredLocation, mount, driveName);
        }

        @Override
        public String mountWritable(String desiredLocation, IWritableMount mount,
                                    String driveName) {
            return parent.mountWritable(desiredLocation, mount, driveName);
        }

        @Override
        public void unmount(String location) {
            parent.unmount(location);
        }

        @Override
        public int getID() {
            return parent.getID();
        }

        @Override
        public void queueEvent(String event, Object[] args) {
            parent.queueEvent(event, args);
        }

        @Override
        public String getAttachmentName() {
            return name;
        }

        @Override
        public IWorkMonitor getMainThreadMonitor() {
            return parent.getMainThreadMonitor();
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        synchronized (computers) {
            computers.add(computer);
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        synchronized (computers) {
            computers.remove(computer);
        }
        // Release our attachments to remote peripherals for this computer.
        Map<String, AttachedRemote> byName;
        synchronized (attachedRemotes) {
            byName = attachedRemotes.remove(computer.getID());
        }
        if (byName != null) {
            for (AttachedRemote ar : byName.values()) {
                try {
                    ar.target.detach(ar.access);
                } catch (Exception ignored) {
                    // already gone; nothing to clean up
                }
            }
        }
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other == this;
    }

    // ---------- Inbound ----------

    /**
     * Deliver a decrypted inbound message to every attached computer that
     * has the channel open. Called on the server thread.
     */
    public void receiveMessage(int channel, int replyChannel,
                              String message, double distance) {
        boolean open;
        synchronized (openChannels) {
            open = openChannels.contains(channel);
        }
        if (!open) return;
        Set<IComputerAccess> snapshot;
        synchronized (computers) {
            snapshot = new HashSet<IComputerAccess>(computers);
        }
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent("modem_message", new Object[]{
                    computer.getAttachmentName(), channel, replyChannel,
                    message, distance,
            });
        }
    }

    // ---------- Helpers ----------

    private static int parseChannel(Object[] args, int index) throws LuaException {
        if (args.length <= index) throw new LuaException("Expected " + (index + 1) + " arguments");
        Object o = args[index];
        if (!(o instanceof Number)) throw new LuaException("Channel must be a number");
        int channel = ((Number) o).intValue();
        if (channel < 0 || channel > 65535) {
            throw new LuaException("Channel out of range (0-65535)");
        }
        return channel;
    }

    /** Coerce a Lua payload to a transmittable string. */
    private static String coerceToString(Object payload) throws LuaException {
        if (payload == null) return "";
        if (payload instanceof String) return (String) payload;
        if (payload instanceof Number || payload instanceof Boolean) {
            return payload.toString();
        }
        throw new LuaException("Secure modem payload must be a string, number, or boolean");
    }
}

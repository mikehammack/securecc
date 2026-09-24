import com.maximarcana.securecc.peripheral.SecureModemPeripheral;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Regression test for the remote "Not attached to this computer" failure.
 *
 * Plethora's MethodWrapperPeripheral keeps a Map<IComputerAccess, ...>
 * populated ONLY by attach(), and callMethod() throws
 * LuaException("Not attached to this computer") when the calling access
 * is not in that map. The old callRemote() built a fresh IComputerAccess
 * wrapper per call and never called attach(), so every remote call to an
 * attach-gated peripheral (e.g. the laser manipulator's fire()) failed.
 *
 * This test drives the real SecureModemPeripheral.callRemote() against a
 * fake that replicates Plethora's attach-gating byte-for-byte in behavior.
 *
 * Run: javac/java with the mod jar + cc-tweaked-classes.jar on the cp.
 */
public class RemoteAttachTest {
    /** Faithful replica of Plethora MethodWrapperPeripheral's gating. */
    static class GatedPeripheral implements IPeripheral {
        final Map<IComputerAccess, Object> accesses =
                new HashMap<IComputerAccess, Object>();
        int attachCount = 0;
        int detachCount = 0;

        @Override public String getType() { return "manipulator"; }
        @Override public String[] getMethodNames() { return new String[]{"fire"}; }

        @Override
        public void attach(IComputerAccess computer) {
            // Real Plethora: builds a ComputerAccessExecutor, attaches it,
            // stores it keyed by the access instance, detaches any previous.
            Object prev = accesses.put(computer, new Object());
            attachCount++;
            if (prev != null) detachCount++; // old executor detached
        }

        @Override
        public void detach(IComputerAccess computer) {
            if (accesses.remove(computer) != null) detachCount++;
        }

        @Override
        public Object[] callMethod(IComputerAccess computer, ILuaContext context,
                                   int method, Object[] args) throws LuaException {
            // Exact replica of the decompiled check:
            //   IResultExecutor e = accesses.get(computer);
            //   if (e == null) throw new LuaException("Not attached to this computer");
            if (!accesses.containsKey(computer)) {
                throw new LuaException("Not attached to this computer");
            }
            return new Object[]{"fired"};
        }

        @Override public boolean equals(IPeripheral other) { return other == this; }
    }

    /** Plain peripheral with no attach-gating (e.g. a disk drive). */
    static class SimplePeripheral implements IPeripheral {
        @Override public String getType() { return "drive"; }
        @Override public String[] getMethodNames() { return new String[]{"isDiskPresent"}; }
        @Override public void attach(IComputerAccess c) { }
        @Override public void detach(IComputerAccess c) { }
        @Override public Object[] callMethod(IComputerAccess c, ILuaContext ctx,
                                             int m, Object[] a) {
            return new Object[]{Boolean.TRUE};
        }
        @Override public boolean equals(IPeripheral other) { return other == this; }
    }

    static class FakeAccess implements IComputerAccess {
        private final int id;
        FakeAccess(int id) { this.id = id; }
        @Override public String mount(String d, IMount m, String n) { return null; }
        @Override public String mountWritable(String d, IWritableMount m, String n) { return null; }
        @Override public void unmount(String l) { }
        @Override public int getID() { return id; }
        @Override public void queueEvent(String e, Object[] a) { }
        @Override public String getAttachmentName() { return "test"; }
    }

    static int failures = 0;

    static void check(boolean cond, String label) {
        System.out.println((cond ? "PASS " : "FAIL ") + label);
        if (!cond) failures++;
    }

    public static void main(String[] args) throws Exception {
        final GatedPeripheral gated = new GatedPeripheral();
        final SimplePeripheral simple = new SimplePeripheral();
        final Map<String, IPeripheral> net = new HashMap<String, IPeripheral>();
        net.put("manipulator_0", gated);
        net.put("drive_0", simple);

        SecureModemPeripheral.Transport transport =
                new SecureModemPeripheral.Transport() {
                    @Override public void transmit(int c, int r, String p) { }
                    @Override public boolean isWireless() { return false; }
                    @Override public Map<String, IPeripheral> scanRemotePeripherals() {
                        return Collections.unmodifiableMap(net);
                    }
                };
        SecureModemPeripheral modem = new SecureModemPeripheral(transport);
        // callRemote is method index 10 in getMethodNames().
        int callRemote = 10;
        FakeAccess computer = new FakeAccess(42);

        // 1. Sanity: the fake really does gate like Plethora (old behavior).
        boolean oldFailed = false;
        try {
            gated.callMethod(new FakeAccess(99), null, 0, new Object[0]);
        } catch (LuaException e) {
            oldFailed = "Not attached to this computer".equals(e.getMessage());
        }
        check(oldFailed, "fake replicates Plethora gating (unattached call throws)");

        // 2. Remote fire through the fixed callRemote: must succeed.
        Object[] r1 = modem.callMethod(computer, null, callRemote,
                new Object[]{"manipulator_0", "fire", 0.0, 0.0, 0.5});
        check(r1.length == 1 && "fired".equals(r1[0]),
                "callRemote('manipulator_0','fire') succeeds, no attach error");

        // 3. Second call reuses the stable attachment (no attach storm).
        Object[] r2 = modem.callMethod(computer, null, callRemote,
                new Object[]{"manipulator_0", "fire", 1.0, 2.0, 0.5});
        check(r2.length == 1 && "fired".equals(r2[0]),
                "second callRemote reuses the attachment");
        check(gated.attachCount == 1,
                "attach called exactly once (attachCount=" + gated.attachCount + ")");

        // 4. A different computer gets its own attachment.
        FakeAccess computer2 = new FakeAccess(43);
        Object[] r3 = modem.callMethod(computer2, null, callRemote,
                new Object[]{"manipulator_0", "fire", 0.0, 0.0, 0.5});
        check(r3.length == 1 && "fired".equals(r3[0]),
                "second computer attaches independently");
        check(gated.attachCount == 2,
                "attach called once per computer (attachCount=" + gated.attachCount + ")");

        // 5. Non-gated peripherals keep working (best-effort attach).
        Object[] r4 = modem.callMethod(computer, null, callRemote,
                new Object[]{"drive_0", "isDiskPresent"});
        check(r4.length == 1 && Boolean.TRUE.equals(r4[0]),
                "non-gated peripheral still answers callRemote");

        // 6. Modem detach releases the remote attachments.
        modem.detach(computer);
        check(gated.detachCount == 1,
                "modem detach() detaches the remote peripheral (detachCount="
                        + gated.detachCount + ")");
        boolean reattachWorks = false;
        try {
            Object[] r5 = modem.callMethod(computer, null, callRemote,
                    new Object[]{"manipulator_0", "fire", 0.0, 0.0, 0.5});
            reattachWorks = r5.length == 1 && "fired".equals(r5[0]);
        } catch (LuaException e) {
            reattachWorks = false;
        }
        check(reattachWorks, "callRemote re-attaches cleanly after detach");

        System.out.println(failures == 0 ? "ALL TESTS PASSED"
                : failures + " TEST(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}

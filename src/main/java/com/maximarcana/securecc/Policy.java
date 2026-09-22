package com.maximarcana.securecc;

/** What a secure computer does while its owner is offline. */
public enum Policy {
    STAY_RUNNING(0, "stay", "Stay Running"),
    LOCK(1, "lock", "Lock"),
    PIN(2, "pin", "PIN Access"),
    FRIENDS(3, "friends", "Friend Access"),
    SHUTDOWN(4, "shutdown", "Shutdown");

    public final int id;
    public final String arg;
    public final String display;

    Policy(int id, String arg, String display) {
        this.id = id;
        this.arg = arg;
        this.display = display;
    }

    public static Policy byId(int id) {
        for (Policy p : values()) {
            if (p.id == id) return p;
        }
        return LOCK;
    }

    public static Policy byArg(String arg) {
        for (Policy p : values()) {
            if (p.arg.equalsIgnoreCase(arg)) return p;
        }
        return null;
    }
}

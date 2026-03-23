package com.rmjtromp.reflection.mapping;

import com.rmjtromp.ReflectionKit;
import com.rmjtromp.reflection.ReflectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MappedProxyTest {

    // ─── Target classes (simulating opaque objects) ─────────────────

    @SuppressWarnings("unused")
    static class RealPlayer {
        private String name = "Steve";
        private int health = 20;
        private boolean alive = true;
        private RealConnection connection = new RealConnection();
        private String _hiddenHandler = "handler_value";

        public String getName() { return name; }
        public int getHealth() { return health; }
        public boolean isAlive() { return alive; }

        public void teleportTo(double x, double y, double z) {
            this.name = "teleported:" + x + "," + y + "," + z;
        }

        public String greet(String message) {
            return "Hello, " + message + "!";
        }
    }

    @SuppressWarnings("unused")
    static class RealConnection {
        private String address = "127.0.0.1";
        private int port = 25565;

        public String getAddress() { return address; }
    }

    // ─── Proxy definitions ──────────────────────────────────────────

    @Proxy
    static abstract class PlayerProxy {
        public String name;
        public int health;
        public boolean alive;
    }

    @Proxy
    static abstract class PlayerWithMethods {
        public String name;

        @Alias("teleportTo")
        public abstract void teleport(double x, double y, double z);

        public abstract String greet(String message);
    }

    @Proxy
    static abstract class ConnectionProxy {
        public String address;
        public int port;
    }

    @Proxy
    static abstract class PlayerWithNestedProxy {
        public String name;
        public ConnectionProxy connection;
    }

    @Proxy
    static abstract class PlayerWithAlias {
        @Alias("_hiddenHandler")
        public String handler;
    }

    @Proxy
    static abstract class PlayerWithGetterFallback {
        public String name;  // no "name" field accessible, falls back to getName()
    }

    @Proxy
    static abstract class PlayerWithMappedBase extends MappedBase {
        public String name;

        public String shout() {
            String n = field("name");
            return n.toUpperCase();
        }

        public String callGreet(String msg) {
            return call("greet", msg);
        }
    }

    @Proxy
    static abstract class PlayerWithMultiAlias {
        @Alias({"nonexistent1", "nonexistent2", "_hiddenHandler"})
        public String handler;
    }

    // ─── Version-specific proxy with concrete method calling abstract ones ──

    @SuppressWarnings("unused")
    static class VersionedTarget {
        private String name = "Steve";

        // Only the "legacy" method exists on this target
        public void legacyTeleport(String location) {
            this.name = "legacy:" + location;
        }
        // newerTeleport does NOT exist
    }

    @Proxy
    static abstract class VersionedProxy {
        public String name;

        @Alias("legacyTeleport")
        protected abstract void legacyTp(String location);

        @Alias("newerTeleport")
        protected abstract void newerTp(String location);

        public void teleport(String location, boolean useLegacy) {
            if (useLegacy) {
                legacyTp(location);
            } else {
                newerTp(location);
            }
        }
    }

    // ─── Not annotated with @Proxy ──────────────────────────────────
    static abstract class NotAProxy {
        public String name;
    }

    // ─── Tests ──────────────────────────────────────────────────────

    @Test
    void basicFieldResolution() {
        RealPlayer real = new RealPlayer();
        PlayerProxy proxy = ReflectionKit.map(real).to(PlayerProxy.class);

        assertEquals("Steve", proxy.name);
        assertEquals(20, proxy.health);
        assertTrue(proxy.alive);
    }

    @Test
    void abstractMethodDelegation() {
        RealPlayer real = new RealPlayer();
        PlayerWithMethods proxy = ReflectionKit.map(real).to(PlayerWithMethods.class);

        assertEquals("Steve", proxy.name);
        assertEquals("Hello, World!", proxy.greet("World"));
    }

    @Test
    void aliasMethodDelegation() {
        RealPlayer real = new RealPlayer();
        PlayerWithMethods proxy = ReflectionKit.map(real).to(PlayerWithMethods.class);

        // teleport() on proxy should call teleportTo() on real via @Alias
        proxy.teleport(1.0, 2.0, 3.0);

        // Verify the underlying object was affected
        assertEquals("teleported:1.0,2.0,3.0", real.getName());
    }

    @Test
    void aliasFieldResolution() {
        RealPlayer real = new RealPlayer();
        PlayerWithAlias proxy = ReflectionKit.map(real).to(PlayerWithAlias.class);

        assertEquals("handler_value", proxy.handler);
    }

    @Test
    void multiAliasFieldResolution() {
        RealPlayer real = new RealPlayer();
        PlayerWithMultiAlias proxy = ReflectionKit.map(real).to(PlayerWithMultiAlias.class);

        // Should skip nonexistent1 and nonexistent2, find _hiddenHandler
        assertEquals("handler_value", proxy.handler);
    }

    @Test
    void nestedProxyAutoWrapping() {
        RealPlayer real = new RealPlayer();
        PlayerWithNestedProxy proxy = ReflectionKit.map(real).to(PlayerWithNestedProxy.class);

        assertEquals("Steve", proxy.name);
        assertNotNull(proxy.connection);
        assertEquals("127.0.0.1", proxy.connection.address);
        assertEquals(25565, proxy.connection.port);
    }

    @Test
    void getterFallback() {
        // RealPlayer has a private "name" field — should still resolve via field access
        RealPlayer real = new RealPlayer();
        PlayerWithGetterFallback proxy = ReflectionKit.map(real).to(PlayerWithGetterFallback.class);

        assertEquals("Steve", proxy.name);
    }

    @Test
    void mappedBaseCallHelper() {
        RealPlayer real = new RealPlayer();
        PlayerWithMappedBase proxy = ReflectionKit.map(real).to(PlayerWithMappedBase.class);

        assertEquals("Steve", proxy.name);
        assertEquals("STEVE", proxy.shout());
        assertEquals("Hello, Claude!", proxy.callGreet("Claude"));
    }

    @Test
    void nullTargetThrows() {
        assertThrows(ReflectionException.class, () -> ReflectionKit.map(null));
    }

    @Test
    void nonProxyAnnotatedThrows() {
        RealPlayer real = new RealPlayer();
        assertThrows(ReflectionException.class, () -> ReflectionKit.map(real).to(NotAProxy.class));
    }

    @Test
    void nonAbstractClassThrows() {
        RealPlayer real = new RealPlayer();
        assertThrows(ReflectionException.class, () -> ReflectionKit.map(real).to(RealPlayer.class));
    }

    @Test
    void concreteMethodCallsAbstractLazily() {
        VersionedTarget target = new VersionedTarget();
        VersionedProxy proxy = ReflectionKit.map(target).to(VersionedProxy.class);

        // Proxy creation succeeds even though newerTeleport doesn't exist on target
        assertEquals("Steve", proxy.name);

        // Calling the branch that exists works fine
        proxy.teleport("nether", true);
        assertEquals("legacy:nether", target.name);

        // Calling the branch that doesn't exist throws at call time
        assertThrows(ReflectionException.class, () -> proxy.teleport("end", false));
    }

}

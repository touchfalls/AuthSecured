package io.github.touchfalls.authsecured.paper.listener;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateLoginTest {

    @Test
    void authenticatedPlayerIsOnlyReplacedFromTheSameAddress() throws Exception {
        InetAddress home = InetAddress.getByName("192.0.2.1");
        InetAddress other = InetAddress.getByName("198.51.100.7");

        assertTrue(PlayerConnectionListener.isForeignReplacement(true, home, other));
        assertFalse(PlayerConnectionListener.isForeignReplacement(true, home, InetAddress.getByName("192.0.2.1")));
        assertFalse(PlayerConnectionListener.isForeignReplacement(false, home, other));
        assertFalse(PlayerConnectionListener.isForeignReplacement(true, null, other));
    }
}

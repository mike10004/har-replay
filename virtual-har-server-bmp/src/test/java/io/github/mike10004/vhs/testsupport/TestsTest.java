package io.github.mike10004.vhs.testsupport;

import com.google.common.net.HostAndPort;
import org.junit.Test;

import java.net.ServerSocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestsTest {

    @Test
    public void test_isStillAlive() throws Exception {
        HostAndPort liveAddress;
        try (ServerSocket ctrl = new ServerSocket(0)) {
            liveAddress = HostAndPort.fromParts("localhost", ctrl.getLocalPort());
            assertTrue("ctrl.getSocketAddress() alive", Tests.isStillAlive(liveAddress));
        }
        assertFalse("after closed", Tests.isStillAlive(liveAddress));
        int port = Tests.findOpenPort();
        assertFalse("unused port still alive", Tests.isStillAlive(HostAndPort.fromParts("localhost", port)));
    }
}

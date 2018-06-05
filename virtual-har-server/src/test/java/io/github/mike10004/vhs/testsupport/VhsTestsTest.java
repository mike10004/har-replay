package io.github.mike10004.vhs.testsupport;

import com.google.common.net.HostAndPort;
import org.junit.Test;

import java.net.ServerSocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VhsTestsTest {

    @Test
    public void test_isStillAlive() throws Exception {
        HostAndPort liveAddress;
        try (ServerSocket ctrl = new ServerSocket(0)) {
            liveAddress = HostAndPort.fromParts("localhost", ctrl.getLocalPort());
            assertTrue("ctrl.getSocketAddress() alive", VhsTests.isStillAlive(liveAddress));
        }
        assertFalse("after closed", VhsTests.isStillAlive(liveAddress));
        int port = VhsTests.findOpenPort();
        assertFalse("unused port still alive", VhsTests.isStillAlive(HostAndPort.fromParts("localhost", port)));
    }
}

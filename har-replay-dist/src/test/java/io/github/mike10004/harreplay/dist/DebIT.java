package io.github.mike10004.harreplay.dist;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class DebIT {

    @BeforeClass
    public static void ignoreOnNonLinuxPlatform() {
        Assume.assumeTrue("this test is ignored if the OS is not Linux because debian-maven-plugin skips the deb-building step if the OS is not Linux", SystemUtils.IS_OS_LINUX);
    }

    @Test
    public void checkDeb() throws Exception {
        File debFile = DistTests.getDebFile();
        System.out.format("%s (%d bytes)%n", debFile, debFile.length());
        assertTrue(debFile.length() > 0);
    }

}

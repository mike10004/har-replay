package io.github.mike10004.harreplay.exec;

import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class HarReplay_NonparamIT extends HarReplayITBase {

    @BeforeClass
    public static void checkAssemblyCreated() {
        ExecTests.assumeExecAssemblyNotSkipped();
    }

    @Test
    public void executeHelp() throws Exception {
        ProcessResult<String, String> result;
        try (ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
            result = execute(processTracker, "--help").await();
        }
        System.out.format("stdout:%n%s%n", result.content().stdout());
        assertEquals("exit code", 0, result.exitCode());
        assertNotEquals("stdout", "", result.content().stdout());
    }

}

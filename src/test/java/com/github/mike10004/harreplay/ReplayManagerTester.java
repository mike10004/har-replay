package com.github.mike10004.harreplay;

import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.github.mike10004.nativehelper.ProgramWithOutputResult;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReplayManagerTester {

    private final Path tempDir;
    private final File harFile;

    public ReplayManagerTester(Path tempDir, File harFile) {
        this.tempDir = tempDir;
        this.harFile = harFile;
    }

    public interface ReplayClient<T> {
        T useReplayServer(Path tempDir, HostAndPort proxy) throws Exception;
    }

    public <T> T exercise(ReplayClient<T> client, @Nullable Integer port) throws Exception {
        ReplayManagerConfig replayManagerConfig = ReplayManagerConfig.auto();
        ReplayManager replay = new ReplayManager(replayManagerConfig);
        ReplaySessionConfig.Builder rscb = ReplaySessionConfig.builder(tempDir);
        if (port != null) {
            rscb.port(port);
        }
        ReplaySessionConfig sessionParams = rscb.build(harFile);
        HostAndPort proxy = HostAndPort.fromParts("localhost", sessionParams.port);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        T result;
        try {
            ListenableFuture<ProgramWithOutputFilesResult> programFuture = replay.startAsync(executorService, sessionParams);
            Futures.addCallback(programFuture, new ProgramInfoFutureCallback());
            result = client.useReplayServer(tempDir, proxy);
            programFuture.cancel(true);
            if (!programFuture.isDone()) {
                try {
                    programFuture.get(3, TimeUnit.SECONDS);
                } catch (java.util.concurrent.CancellationException ignore) {
                }
            }
            System.out.println("program future has finished");
        } finally {
            if (!executorService.isShutdown()) {
                System.out.println("shutting down");
                executorService.shutdownNow();
                System.out.println("finished shutdown");
            }
        }
        return result;
    }


    private static class ProgramInfoFutureCallback implements FutureCallback<ProgramWithOutputResult> {
        @Override
        public void onSuccess(ProgramWithOutputResult result) {
            System.out.println("program finished: " + result);
            if (result.getExitCode() != 0) {
                try {
                    result.getStdout().copyTo(System.out);
                    result.getStderr().copyTo(System.err);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                }
            }
        }

        @Override
        public void onFailure(Throwable t) {
            if (!(t instanceof java.util.concurrent.CancellationException)) {
                t.printStackTrace(System.err);
            } else {
                System.out.println("program was cancelled");
            }
        }
    }
}

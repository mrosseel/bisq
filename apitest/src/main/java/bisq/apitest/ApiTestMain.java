/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest;

import bisq.common.config.BisqHelpFormatter;
import bisq.common.util.Utilities;

import java.io.IOException;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.apitest.config.BisqAppConfig.*;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.config.BisqAppConfig;
import bisq.apitest.linux.BisqApp;
import bisq.apitest.linux.BitcoinDaemon;

/**
 * ApiTest Application
 *
 * Requires bitcoind v0.19.1
 */
@Slf4j
public class ApiTestMain {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;

    @Nullable
    private SetupTask bitcoindTask;
    @Nullable
    private Future<SetupTask.Status> bitcoindTaskFuture;
    @Nullable
    private SetupTask seedNodeTask;
    @Nullable
    private Future<SetupTask.Status> seedNodeTaskFuture;
    @Nullable
    private SetupTask arbNodeTask;
    @Nullable
    private Future<SetupTask.Status> arbNodeTaskFuture;
    @Nullable
    private SetupTask aliceNodeTask;
    @Nullable
    private Future<SetupTask.Status> aliceNodeTaskFuture;
    @Nullable
    private SetupTask bobNodeTask;
    @Nullable
    private Future<SetupTask.Status> bobNodeTaskFuture;

    private ApiTestConfig config;

    public static void main(String[] args) {
        new ApiTestMain().execute(args);
    }

    public void execute(@SuppressWarnings("unused") String[] args) {
        try {
            verifyNotWindows();

            log.info("Starting...");

            config = new ApiTestConfig(args);
            if (config.helpRequested) {
                config.printHelp(System.out,
                        new BisqHelpFormatter(
                                "Bisq ApiTest",
                                "bisq-apitest",
                                "0.1.0"));
                System.exit(EXIT_SUCCESS);
            }

            // Start each background process from an executor, then add a shutdown hook.
            ExecutorService executor = Executors.newFixedThreadPool(config.numSetupTasks);
            CountDownLatch countdownLatch = new CountDownLatch(config.numSetupTasks);
            startBackgroundProcesses(executor, countdownLatch);
            installShutdownHook();

            // Wait for all submitted startup tasks to decrement the count of the latch.
            Objects.requireNonNull(countdownLatch).await();

            // Verify each startup task's future is done.
            verifyStartupCompleted();

            if (config.skipTests) {
                log.info("Skipping tests ...");
            } else {
                log.info("Run tests now ...");
                SECONDS.sleep(3);
                new SmokeTestBitcoind(config).runSmokeTest();
            }

            if (config.shutdownAfterTests) {
                log.info("Shutting down executor service ...");
                executor.shutdownNow();
                executor.awaitTermination(10, SECONDS);
                exit(EXIT_SUCCESS);
            } else {
                log.info("Not shutting down executor service ...");
                log.info("Test setup processes will run until ^C / kill -15 is rcvd ...");
            }

        } catch (Throwable ex) {
            err.println("Fault: An unexpected error occurred. " +
                    "Please file a report at https://bisq.network/issues");
            ex.printStackTrace(err);
            exit(EXIT_FAILURE);
        }
    }

    // Starts bitcoind and bisq apps (seednode, arbnode, etc...)
    private void startBackgroundProcesses(ExecutorService executor,
                                          CountDownLatch countdownLatch)
            throws InterruptedException, IOException {
        // The configured number of setup tasks determines which bisq apps are started in
        // the background, and in what order.
        //
        // If config.numSetupTasks = 0, no setup tasks are run.  If 1, the bitcoind
        // process is started in the background.  If 2, bitcoind and seednode.
        // If 3, bitcoind, seednode and arbnode are started.  If 4, bitcoind, seednode,
        // arbnode, and alicenode are started.  If 5,  bitcoind, seednode, arbnode,
        // alicenode and bobnode are started.
        //
        // This affords an easier way to choose which setup tasks are run, rather than
        // commenting and uncommenting code blocks.  You have to remember seednode
        // depends on bitcoind, arbnode on seednode, and that bob & alice cannot trade
        // unless arbnode is running with a registered mediator and refund agent.
        if (config.numSetupTasks > 0) {
            BitcoinDaemon bitcoinDaemon = new BitcoinDaemon(config);
            bitcoinDaemon.verifyBitcoinConfig(true);
            bitcoindTask = new SetupTask(bitcoinDaemon, countdownLatch);
            bitcoindTaskFuture = executor.submit(bitcoindTask);
            SECONDS.sleep(5);
            bitcoinDaemon.verifyBitcoindRunning();
        }
        if (config.numSetupTasks > 1) {
            startBisqApp(seednode, executor, countdownLatch);
        }
        if (config.numSetupTasks > 2) {
            startBisqApp(config.runArbNodeAsDesktop ? arbdesktop : arbdaemon, executor, countdownLatch);
        }
        if (config.numSetupTasks > 3) {
            startBisqApp(config.runAliceNodeAsDesktop ? alicedesktop : alicedaemon, executor, countdownLatch);
        }
        if (config.numSetupTasks > 4) {
            startBisqApp(config.runBobNodeAsDesktop ? bobdesktop : bobdaemon, executor, countdownLatch);
        }
    }

    private void startBisqApp(BisqAppConfig bisqAppConfig,
                              ExecutorService executor,
                              CountDownLatch countdownLatch)
            throws IOException, InterruptedException {

        BisqApp bisqApp;
        switch (bisqAppConfig) {
            case seednode:
                bisqApp = createBisqApp(seednode);
                seedNodeTask = new SetupTask(bisqApp, countdownLatch);
                seedNodeTaskFuture = executor.submit(seedNodeTask);
                break;
            case arbdaemon:
            case arbdesktop:
                bisqApp = createBisqApp(config.runArbNodeAsDesktop ? arbdesktop : arbdaemon);
                arbNodeTask = new SetupTask(bisqApp, countdownLatch);
                arbNodeTaskFuture = executor.submit(arbNodeTask);
                break;
            case alicedaemon:
            case alicedesktop:
                bisqApp = createBisqApp(config.runAliceNodeAsDesktop ? alicedesktop : alicedaemon);
                aliceNodeTask = new SetupTask(bisqApp, countdownLatch);
                aliceNodeTaskFuture = executor.submit(aliceNodeTask);
                break;
            case bobdaemon:
            case bobdesktop:
                bisqApp = createBisqApp(config.runBobNodeAsDesktop ? bobdesktop : bobdaemon);
                bobNodeTask = new SetupTask(bisqApp, countdownLatch);
                bobNodeTaskFuture = executor.submit(bobNodeTask);
                break;
            default:
                throw new IllegalStateException("Unknown Bisq App " + bisqAppConfig.appName);
        }
        SECONDS.sleep(5);
        if (bisqApp.hasStartupExceptions()) {
            for (Throwable t : bisqApp.getStartupExceptions()) {
                log.error("", t);
            }
            exit(EXIT_FAILURE);
        }
    }

    private BisqApp createBisqApp(BisqAppConfig bisqAppConfig)
            throws IOException, InterruptedException {
        BisqApp bisqNode = new BisqApp(bisqAppConfig, config);
        bisqNode.verifyAppNotRunning();
        bisqNode.verifyAppDataDirInstalled();
        return bisqNode;
    }

    private void verifyStartupCompleted()
            throws ExecutionException, InterruptedException {
        if (config.numSetupTasks > 0)
            verifyStartupCompleted(bitcoindTaskFuture);

        if (config.numSetupTasks > 1)
            verifyStartupCompleted(seedNodeTaskFuture);

        if (config.numSetupTasks > 2)
            verifyStartupCompleted(arbNodeTaskFuture);

        if (config.numSetupTasks > 3)
            verifyStartupCompleted(aliceNodeTaskFuture);

        if (config.numSetupTasks > 4)
            verifyStartupCompleted(bobNodeTaskFuture);
    }

    private void verifyStartupCompleted(Future<SetupTask.Status> futureStatus)
            throws ExecutionException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (futureStatus.isDone()) {
                log.info("{} completed startup at {} {}",
                        futureStatus.get().getName(),
                        futureStatus.get().getStartTime().toLocalDate(),
                        futureStatus.get().getStartTime().toLocalTime());
                return;
            } else {
                // We are giving the thread more time to terminate after the countdown
                // latch reached 0.  If we are running only bitcoind, we need to be even
                // more lenient.
                SECONDS.sleep(config.numSetupTasks == 1 ? 2 : 1);
            }
        }
        throw new IllegalStateException(format("%s did not complete startup", futureStatus.get().getName()));
    }

    private void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("Running shutdown hook ...");
                if (bobNodeTask != null && bobNodeTask.getLinuxProcess() != null)
                    bobNodeTask.getLinuxProcess().shutdown();

                SECONDS.sleep(3);

                if (aliceNodeTask != null && aliceNodeTask.getLinuxProcess() != null)
                    aliceNodeTask.getLinuxProcess().shutdown();

                SECONDS.sleep(3);

                if (arbNodeTask != null && arbNodeTask.getLinuxProcess() != null)
                    arbNodeTask.getLinuxProcess().shutdown();

                SECONDS.sleep(3);

                if (seedNodeTask != null && seedNodeTask.getLinuxProcess() != null)
                    seedNodeTask.getLinuxProcess().shutdown();

                SECONDS.sleep(3);

                if (bitcoindTask != null && bitcoindTask.getLinuxProcess() != null)
                    bitcoindTask.getLinuxProcess().shutdown();

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }));
    }

    private void verifyNotWindows() {
        if (Utilities.isWindows())
            throw new RuntimeException("ApiTest not supported on Windows");
    }
}

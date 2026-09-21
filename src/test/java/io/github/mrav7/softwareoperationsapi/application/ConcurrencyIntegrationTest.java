package io.github.mrav7.softwareoperationsapi.application;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkLog;
import io.github.mrav7.softwareoperationsapi.domain.WorkLogType;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ConcurrencyIntegrationTest {
    private static final long WAIT_SECONDS = 10;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private SoftwareComponentService componentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactions;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        transactions = new TransactionTemplate(transactionManager);
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void staleWorkOrderWriteCannotSilentlyOverwriteAnotherMutation() throws Exception {
        WorkOrder original = createWorkOrder("stale-work-order");
        CyclicBarrier bothLoaded = new CyclicBarrier(2);

        ConcurrentResult result = runConcurrently(
                transactional(() -> {
                    WorkOrder workOrder = reloadWorkOrder(original.getId());
                    await(bothLoaded);
                    workOrder.changeTitle("Updated concurrently");
                    workOrderRepository.flush();
                }),
                transactional(() -> {
                    WorkOrder workOrder = reloadWorkOrder(original.getId());
                    await(bothLoaded);
                    workOrder.changeDescription("Changed concurrently");
                    workOrderRepository.flush();
                }));

        assertOneOptimisticFailure(result);
        WorkOrder persisted = reloadWorkOrder(original.getId());
        boolean titleWon = persisted.getTitle().equals("Updated concurrently")
                && persisted.getDescription().equals("Original description");
        boolean descriptionWon = persisted.getTitle().equals("Original title")
                && persisted.getDescription().equals("Changed concurrently");
        assertTrue(titleWon ^ descriptionWon);
    }

    @Test
    void competingLifecycleTransactionsRollbackLosingStatusChange() throws Exception {
        WorkOrder original = createWorkOrder("lifecycle-conflict");
        CyclicBarrier bothLoaded = new CyclicBarrier(2);

        ConcurrentResult result = runConcurrently(
                transactional(() -> {
                    WorkOrder workOrder = reloadWorkOrder(original.getId());
                    await(bothLoaded);
                    workOrder.plan();
                    workLogRepository.save(WorkLog.statusChange(
                            workOrder, "Work order planned."));
                    workOrderRepository.flush();
                }),
                transactional(() -> {
                    WorkOrder workOrder = reloadWorkOrder(original.getId());
                    await(bothLoaded);
                    workOrder.cancel("Cancelled concurrently");
                    workLogRepository.save(WorkLog.statusChange(
                            workOrder, "Work order cancelled: Cancelled concurrently"));
                    workOrderRepository.flush();
                }));

        assertOneOptimisticFailure(result);
        WorkOrder persisted = reloadWorkOrder(original.getId());
        List<WorkLog> logs = workLogRepository
                .findByWorkOrder_IdOrderByCreatedAtAscIdAsc(original.getId());
        assertEquals(1, logs.size());
        assertEquals(WorkLogType.STATUS_CHANGE, logs.getFirst().getType());
        if (persisted.getStatus() == WorkOrderStatus.PLANNED) {
            assertEquals("Work order planned.", logs.getFirst().getMessage());
        } else {
            assertEquals(WorkOrderStatus.CANCELLED, persisted.getStatus());
            assertEquals("Work order cancelled: Cancelled concurrently",
                    logs.getFirst().getMessage());
        }
    }

    @Test
    void updateAndTransitionCannotOverwriteEachOtherOrLeaveFalseHistory() throws Exception {
        WorkOrder original = createWorkOrder("update-transition");
        CyclicBarrier bothLoaded = new CyclicBarrier(2);

        ConcurrentResult result = runConcurrently(
                transactional(() -> {
                    WorkOrder workOrder = reloadWorkOrder(original.getId());
                    await(bothLoaded);
                    workOrder.changeTitle("Updated concurrently");
                    workOrderRepository.flush();
                }),
                transactional(() -> {
                    WorkOrder workOrder = reloadWorkOrder(original.getId());
                    await(bothLoaded);
                    workOrder.plan();
                    workLogRepository.save(WorkLog.statusChange(
                            workOrder, "Work order planned."));
                    workOrderRepository.flush();
                }));

        assertOneOptimisticFailure(result);
        WorkOrder persisted = reloadWorkOrder(original.getId());
        List<WorkLog> logs = workLogRepository
                .findByWorkOrder_IdOrderByCreatedAtAscIdAsc(original.getId());
        if (persisted.getStatus() == WorkOrderStatus.PLANNED) {
            assertEquals("Original title", persisted.getTitle());
            assertEquals(1, logs.size());
            assertEquals("Work order planned.", logs.getFirst().getMessage());
        } else {
            assertEquals(WorkOrderStatus.CREATED, persisted.getStatus());
            assertEquals("Updated concurrently", persisted.getTitle());
            assertTrue(logs.isEmpty());
        }
    }

    @Test
    void createAndDeactivateCannotCommitAnInactiveComponentWithNewActiveWork()
            throws Exception {
        SoftwareComponent component = createComponent("create-deactivate-source");

        ConcurrentResult result = runWithProductionComponentLockObserved(
                () -> workOrderService.create(
                        component.getId(), "Concurrent creation", null,
                        WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.HIGH, null),
                () -> componentService.deactivate(component.getId()));

        SoftwareComponent persistedComponent = reloadComponent(component.getId());
        long orderCount = workOrderRepository.count();
        assertNull(result.firstFailure());
        assertTrue(hasCause(result.secondFailure(), ComponentHasActiveWorkException.class));
        assertTrue(persistedComponent.isActive());
        assertEquals(1, orderCount);
        assertFalse(!persistedComponent.isActive() && orderCount > 0);
    }

    @Test
    void reassignAndTargetDeactivationCannotCommitWorkOnAnInactiveTarget()
            throws Exception {
        SoftwareComponent source = createComponent("reassign-source");
        SoftwareComponent target = createComponent("reassign-target");
        WorkOrder workOrder = workOrderService.create(
                source.getId(), "Concurrent reassignment", null,
                WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.HIGH, null);
        UpdateWorkOrderCommand reassign = new UpdateWorkOrderCommand(
                true, target.getId(),
                false, null,
                false, null,
                false, null,
                false, null,
                false, null);

        ConcurrentResult result = runWithProductionComponentLockObserved(
                () -> workOrderService.modify(workOrder.getId(), reassign),
                () -> componentService.deactivate(target.getId()));

        SoftwareComponent persistedTarget = reloadComponent(target.getId());
        WorkOrder persistedOrder = reloadWorkOrder(workOrder.getId());
        assertNull(result.firstFailure());
        assertTrue(hasCause(result.secondFailure(), ComponentHasActiveWorkException.class));
        assertTrue(persistedTarget.isActive());
        assertEquals(target.getId(), persistedOrder.getComponent().getId());
        assertFalse(!persistedTarget.isActive()
                && persistedOrder.getComponent().getId().equals(target.getId()));
    }

    @Test
    void staleSoftwareComponentWriteCannotSilentlyOverwriteAnotherMutation()
            throws Exception {
        SoftwareComponent original = createComponent("stale-component");
        CyclicBarrier bothLoaded = new CyclicBarrier(2);

        ConcurrentResult result = runConcurrently(
                transactional(() -> {
                    SoftwareComponent component = reloadComponent(original.getId());
                    await(bothLoaded);
                    component.changeName("renamed-concurrently-" + UUID.randomUUID());
                    componentRepository.flush();
                }),
                transactional(() -> {
                    SoftwareComponent component = reloadComponent(original.getId());
                    await(bothLoaded);
                    component.changeDescription("Changed concurrently");
                    componentRepository.flush();
                }));

        assertOneOptimisticFailure(result);
        SoftwareComponent persisted = reloadComponent(original.getId());
        boolean nameWon = !persisted.getName().equals(original.getName())
                && persisted.getDescription().equals("Original description");
        boolean descriptionWon = persisted.getName().equals(original.getName())
                && persisted.getDescription().equals("Changed concurrently");
        assertTrue(nameWon ^ descriptionWon);
    }

    private ThrowingOperation transactional(ThrowingOperation operation) {
        return () -> transactions.executeWithoutResult(ignored -> operation.run());
    }

    private ConcurrentResult runConcurrently(
            ThrowingOperation first, ThrowingOperation second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> firstResult = executor.submit(
                    () -> runAfterStart(ready, start, first));
            Future<Throwable> secondResult = executor.submit(
                    () -> runAfterStart(ready, start, second));
            assertTrue(ready.await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "Concurrent operations did not become ready in time");
            start.countDown();
            return new ConcurrentResult(
                    firstResult.get(WAIT_SECONDS, TimeUnit.SECONDS),
                    secondResult.get(WAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS),
                    "Concurrent test executor did not terminate");
        }
    }

    private ConcurrentResult runWithProductionComponentLockObserved(
            ThrowingOperation protectedOperation, ThrowingOperation competingOperation)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection writeGate = dataSource.getConnection()) {
            writeGate.setAutoCommit(false);
            int gatePid = lockWorkOrderWrites(writeGate);

            Future<Throwable> protectedResult = executor.submit(
                    () -> captureFailure(protectedOperation));
            int protectedPid = awaitBlockedBy(
                    gatePid,
                    protectedResult,
                    "Production service did not reach its gated WorkOrder write");

            Future<Throwable> competingResult = executor.submit(
                    () -> captureFailure(competingOperation));
            awaitBlockedBy(
                    protectedPid,
                    competingResult,
                    "Competing deactivation was not blocked by the production service lock");

            writeGate.commit();
            return new ConcurrentResult(
                    protectedResult.get(WAIT_SECONDS, TimeUnit.SECONDS),
                    competingResult.get(WAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS),
                    "Concurrent test executor did not terminate");
        }
    }

    private static int lockWorkOrderWrites(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("LOCK TABLE work_order IN SHARE MODE");
            try (ResultSet result = statement.executeQuery("SELECT pg_backend_pid()")) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int awaitBlockedBy(
            int blockerPid, Future<Throwable> operation, String failureMessage)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            List<Integer> blockedPids = jdbcTemplate.queryForList("""
                    SELECT activity.pid
                    FROM pg_stat_activity activity
                    WHERE activity.datname = current_database()
                      AND ? = ANY(pg_blocking_pids(activity.pid))
                    """, Integer.class, blockerPid);
            if (!blockedPids.isEmpty()) {
                return blockedPids.getFirst();
            }
            if (operation.isDone()) {
                Throwable failure = operation.get(WAIT_SECONDS, TimeUnit.SECONDS);
                throw new AssertionError(failureMessage + "; operation completed first", failure);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            if (Thread.interrupted()) {
                throw new InterruptedException("Interrupted while observing PostgreSQL locks");
            }
        }
        throw new AssertionError(failureMessage + " within " + WAIT_SECONDS + " seconds");
    }

    private static Throwable runAfterStart(
            CountDownLatch ready, CountDownLatch start, ThrowingOperation operation) {
        ready.countDown();
        try {
            if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                return new IllegalStateException("Concurrent operations were not released in time");
            }
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static Throwable captureFailure(ThrowingOperation operation) {
        try {
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void await(CountDownLatch latch, String timeoutMessage) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test thread was interrupted", exception);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent transactions did not load in time", exception);
        }
    }

    private static void assertOneOptimisticFailure(ConcurrentResult result) {
        assertEquals(1, result.successCount());
        assertEquals(1, result.failures().stream()
                .filter(failure -> hasCause(
                        failure, OptimisticLockingFailureException.class))
                .count());
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private SoftwareComponent createComponent(String prefix) {
        return componentRepository.saveAndFlush(new SoftwareComponent(
                prefix + "-" + UUID.randomUUID(), "Original description"));
    }

    private WorkOrder createWorkOrder(String componentPrefix) {
        SoftwareComponent component = createComponent(componentPrefix);
        return workOrderService.create(
                component.getId(), "Original title", "Original description",
                WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.HIGH, null);
    }

    private WorkOrder reloadWorkOrder(UUID id) {
        return workOrderRepository.findById(id).orElseThrow();
    }

    private SoftwareComponent reloadComponent(UUID id) {
        return componentRepository.findById(id).orElseThrow();
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private record ConcurrentResult(Throwable firstFailure, Throwable secondFailure) {
        int successCount() {
            return (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0);
        }

        List<Throwable> failures() {
            return Stream.of(firstFailure, secondFailure)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}

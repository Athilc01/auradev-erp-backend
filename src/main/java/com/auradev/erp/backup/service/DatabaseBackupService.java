package com.auradev.erp.backup.service;

import com.auradev.erp.audit.service.AuditService;
import com.auradev.erp.auth.security.UserPrincipal;
import com.auradev.erp.backup.dto.DatabaseBackupResponse;
import com.auradev.erp.backup.dto.DatabaseBackupStatusResponse;
import com.auradev.erp.backup.entity.DatabaseBackup;
import com.auradev.erp.backup.repository.DatabaseBackupRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates a PostgreSQL custom-format archive with pg_dump. It is intentionally
 * disabled by default: cloud installations should use their provider's backup
 * service, while an on-premise installer explicitly enables this feature.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.of("UTC"));

    private final DatabaseBackupRepository backups;
    private final AuditService auditService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "database-backup-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.backup.enabled:false}")
    private boolean enabled;
    @Value("${app.backup.dir:}")
    private String backupDirectory;
    @Value("${app.backup.pg-dump-path:pg_dump}")
    private String pgDumpPath;
    @Value("${app.backup.mode:PG_DUMP}")
    private String backupMode;
    @Value("${app.backup.docker-container:}")
    private String dockerContainer;
    @Value("${app.backup.retention-days:30}")
    private int retentionDays;
    @Value("${app.backup.timeout-seconds:900}")
    private long timeoutSeconds;
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    @Value("${spring.datasource.username}")
    private String databaseUser;
    @Value("${spring.datasource.password}")
    private String databasePassword;

    public DatabaseBackupStatusResponse status() {
        List<DatabaseBackupResponse> history = backups.findAllByOrderByStartedAtDesc(PageRequest.of(0, 25))
                .stream().map(this::toResponse).toList();
        return new DatabaseBackupStatusResponse(enabled, hasStorageDirectory(), normalizedMode(),
                "Every day at 2:00 AM (server time)", Math.max(retentionDays, 1), history);
    }

    public DatabaseBackupResponse requestManualBackup(UserPrincipal principal) {
        if (!enabled) {
            throw new IllegalStateException("Automatic backups are not enabled on this server. Configure APP_BACKUP_ENABLED=true.");
        }
        if (!hasStorageDirectory()) {
            throw new IllegalStateException("Backup storage is not configured. Configure APP_BACKUP_DIR to a writable server folder.");
        }
        DatabaseBackup backup = queue("MANUAL", principal == null ? null : principal.getId());
        auditService.log("DATABASE_BACKUP_REQUESTED", "DATABASE_BACKUP", backup.getId(),
                Map.of("fileName", backup.getFileName(), "trigger", "MANUAL"));
        return toResponse(backup);
    }

    @Scheduled(cron = "${app.backup.cron:0 0 2 * * *}")
    public void runScheduledBackup() {
        if (!enabled || !hasStorageDirectory()) return;
        queue("SCHEDULED", null);
    }

    private DatabaseBackup queue(String triggerType, UUID userId) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A database backup is already running. Wait for it to finish before starting another.");
        }
        DatabaseBackup backup = new DatabaseBackup();
        backup.setFileName("mercantile-" + FILE_TIME.format(Instant.now()) + ".dump");
        backup.setStatus("QUEUED");
        backup.setTriggerType(triggerType);
        backup.setStartedAt(Instant.now());
        backup.setCreatedBy(userId);
        backup = backups.save(backup);
        UUID backupId = backup.getId();
        executor.submit(() -> performBackup(backupId));
        return backup;
    }

    private void performBackup(UUID backupId) {
        DatabaseBackup backup = backups.findById(backupId).orElse(null);
        if (backup == null) {
            running.set(false);
            return;
        }
        try {
            backup.setStatus("RUNNING");
            backups.save(backup);
            Path directory = Path.of(backupDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path destination = directory.resolve(backup.getFileName()).normalize();
            if (!destination.startsWith(directory)) throw new IllegalStateException("Unsafe backup destination");
            Path temporary = destination.resolveSibling(destination.getFileName() + ".part");

            ProcessBuilder processBuilder = new ProcessBuilder(backupCommand(temporary));
            processBuilder.redirectErrorStream(false);
            if ("DOCKER".equals(normalizedMode())) {
                // pg_dump runs inside the container, so stream its custom-format
                // archive directly to the protected Windows backup folder.
                processBuilder.redirectOutput(temporary.toFile());
            } else {
                processBuilder.environment().put("PGPASSWORD", databasePassword);
            }
            Process process = processBuilder.start();
            boolean finished = process.waitFor(Math.max(timeoutSeconds, 30), TimeUnit.SECONDS);
            String output = new String(process.getErrorStream().readAllBytes()).trim();
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("pg_dump timed out after " + Math.max(timeoutSeconds, 30) + " seconds");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(output.isBlank() ? "pg_dump exited with code " + process.exitValue() : truncate(output));
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                // Some external or networked drives do not support atomic moves.
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            backup.setStatus("COMPLETED");
            backup.setSizeBytes(Files.size(destination));
            backup.setCompletedAt(Instant.now());
            backups.save(backup);
            cleanupOldBackups(directory);
            log.info("Database backup completed: {}", backup.getFileName());
        } catch (Exception e) {
            log.error("Database backup failed", e);
            backup.setStatus("FAILED");
            backup.setErrorMessage(truncate(e.getMessage()));
            backup.setCompletedAt(Instant.now());
            backups.save(backup);
        } finally {
            running.set(false);
        }
    }

    private void cleanupOldBackups(Path directory) throws IOException {
        Instant cutoff = Instant.now().minusSeconds(Math.max(retentionDays, 1) * 86_400L);
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().startsWith("mercantile-")
                            && path.getFileName().toString().endsWith(".dump"))
                    .filter(path -> {
                        try { return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff); }
                        catch (IOException ignored) { return false; }
                    })
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); }
                        catch (IOException e) { log.warn("Could not remove expired backup {}", path, e); }
                    });
        }
        backups.deleteByCompletedAtBefore(cutoff);
    }

    private boolean hasStorageDirectory() {
        return backupDirectory != null && !backupDirectory.isBlank();
    }

    private List<String> backupCommand(Path temporary) {
        DatabaseTarget target = parseJdbcTarget(jdbcUrl);
        if ("DOCKER".equals(normalizedMode())) {
            if (dockerContainer == null || dockerContainer.isBlank()) {
                throw new IllegalStateException("Docker backup mode requires APP_BACKUP_DOCKER_CONTAINER");
            }
            return List.of(
                    "docker", "exec", dockerContainer.trim(),
                    "pg_dump", "--format=custom", "--no-owner", "--no-privileges",
                    "--username=" + databaseUser,
                    target.database());
        }
        return List.of(
                pgDumpPath, "--format=custom", "--no-owner", "--no-privileges",
                "--host=" + target.host(), "--port=" + target.port(),
                "--username=" + databaseUser, "--file=" + temporary, target.database());
    }

    private String normalizedMode() {
        return "DOCKER".equalsIgnoreCase(backupMode) ? "DOCKER" : "PG_DUMP";
    }

    private static DatabaseTarget parseJdbcTarget(String jdbc) {
        try {
            URI uri = URI.create(jdbc.substring("jdbc:".length()));
            String database = uri.getPath();
            if (uri.getHost() == null || database == null || database.length() <= 1) {
                throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL");
            }
            return new DatabaseTarget(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 5432, database.substring(1));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read PostgreSQL backup connection details", e);
        }
    }

    private DatabaseBackupResponse toResponse(DatabaseBackup backup) {
        return new DatabaseBackupResponse(backup.getId(), backup.getFileName(), backup.getStatus(), backup.getTriggerType(),
                backup.getStartedAt(), backup.getCompletedAt(), backup.getSizeBytes(), backup.getErrorMessage());
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) return "Backup command failed. See the server log for details.";
        return value.length() <= 950 ? value : value.substring(0, 950) + "…";
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private record DatabaseTarget(String host, int port, String database) {}
}

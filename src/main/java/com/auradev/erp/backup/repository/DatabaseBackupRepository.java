package com.auradev.erp.backup.repository;

import com.auradev.erp.backup.entity.DatabaseBackup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DatabaseBackupRepository extends JpaRepository<DatabaseBackup, UUID> {
    List<DatabaseBackup> findAllByOrderByStartedAtDesc(Pageable pageable);
    void deleteByCompletedAtBefore(Instant cutoff);
}

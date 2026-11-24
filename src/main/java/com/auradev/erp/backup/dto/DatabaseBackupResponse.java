package com.auradev.erp.backup.dto;

import java.time.Instant;
import java.util.UUID;

public record DatabaseBackupResponse(
        UUID id,
        String fileName,
        String status,
        String triggerType,
        Instant startedAt,
        Instant completedAt,
        Long sizeBytes,
        String errorMessage
) {}

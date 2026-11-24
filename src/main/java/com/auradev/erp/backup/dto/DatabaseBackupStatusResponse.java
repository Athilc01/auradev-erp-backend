package com.auradev.erp.backup.dto;

import java.util.List;

public record DatabaseBackupStatusResponse(
        boolean enabled,
        boolean storageConfigured,
        String backupMode,
        String scheduleDescription,
        int retentionDays,
        List<DatabaseBackupResponse> history
) {}

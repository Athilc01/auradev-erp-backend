# On-premise database backups

Mercantile can create PostgreSQL recovery archives on a local server. It is disabled by default so cloud deployments continue to rely on their managed database provider.

## Server configuration

Set these values in the server environment file before starting Mercantile:

```properties
APP_BACKUP_ENABLED=true
APP_BACKUP_DIR=D:\\MercantileBackups
APP_BACKUP_RETENTION_DAYS=30
# APP_BACKUP_PG_DUMP_PATH=C:\\Program Files\\PostgreSQL\\16\\bin\\pg_dump.exe
```

`APP_BACKUP_DIR` must be a protected, writable folder outside the Mercantile installation directory. PostgreSQL's `pg_dump` must be installed and available on `PATH`, or configured explicitly with `APP_BACKUP_PG_DUMP_PATH`.

If the local PostgreSQL database runs in Docker, Mercantile can run `pg_dump` inside that container instead. Docker Desktop must be running and the account that launches Mercantile must be allowed to run Docker commands.

```properties
APP_BACKUP_MODE=DOCKER
APP_BACKUP_DOCKER_CONTAINER=auradev-postgres
```

The system runs one backup daily at 2:00 AM in the server's local time. A tenant administrator can also use **Administration → Backups → Create backup now**. The screen lists success, failure details, archive size, and the last 25 recorded runs.

## Recovery

Archives use PostgreSQL custom format (`.dump`). Do not restore through the normal store application. Stop Mercantile first and have an authorised technical administrator restore the archive into the correct PostgreSQL database using `pg_restore`.

```powershell
pg_restore --clean --if-exists --no-owner --host localhost --port 5433 --username auradev --dbname auradev_erp D:\MercantileBackups\mercantile-YYYYMMDD-HHMMSS.dump
```

Restoring replaces data. Always take a fresh backup before a recovery attempt and verify the restored application on the server before terminals resume work.

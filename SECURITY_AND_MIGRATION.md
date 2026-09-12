# Security configuration and database migration

## Existing database

Run `sql/migration_v2_security.sql` once. For a fresh database, run `sql/schema.sql` instead.

The migration makes teacher ID 1 an administrator. Other teacher accounts keep the `teacher` role.
The original demo password `123456` is converted from plaintext to a salted legacy hash and is
automatically upgraded to BCrypt on the next successful login.

## Runtime configuration

Copy the variables from `.env.example` into the service environment. Production deployments must
set `REHAB_DB_PASSWORD` and `REHAB_JWT_SECRET`. Configure `REHAB_WS_ALLOWED_ORIGINS` with the actual
frontend origin; multiple origins can be comma-separated.

The empty database-password fallback and development JWT fallback exist only for local development.

#!/bin/bash
# Creates one MySQL user per service with privileges on ITS OWN schema only, so no service can
# read another service's tables (no shared database). Passwords come from the environment
# (.env -> docker-compose.yml -> mysql container). Runs once, after 01-create-schemas.sql.
set -euo pipefail

create_user() {
  local user="$1" password="$2" schema="$3"
  echo "[orgwise-init] creating user '${user}' for schema '${schema}'"
  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${user}'@'%' IDENTIFIED BY '${password}';
ALTER USER '${user}'@'%' IDENTIFIED BY '${password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES
  ON \`${schema}\`.* TO '${user}'@'%';
SQL
}

create_user "${IDENTITY_DB_USER:-identity_user}"   "${IDENTITY_DB_PASSWORD:-identity_pass}"   orgwise_identity
create_user "${EVENT_DB_USER:-event_user}"         "${EVENT_DB_PASSWORD:-event_pass}"         orgwise_event
create_user "${EXPENSE_DB_USER:-expense_user}"     "${EXPENSE_DB_PASSWORD:-expense_pass}"     orgwise_expense
create_user "${TREASURY_DB_USER:-treasury_user}"   "${TREASURY_DB_PASSWORD:-treasury_pass}"   orgwise_treasury
create_user "${REPORTING_DB_USER:-reporting_user}" "${REPORTING_DB_PASSWORD:-reporting_pass}" orgwise_reporting

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"
echo "[orgwise-init] done"

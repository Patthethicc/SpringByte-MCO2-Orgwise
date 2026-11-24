-- Orgwise MCO2: one schema per bounded context (database-per-service).
-- Runs once, on the first start of the MySQL container (empty data volume).
-- Users and grants are created by 02-create-users.sh from the .env passwords.

CREATE DATABASE IF NOT EXISTS orgwise_identity  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orgwise_event     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orgwise_expense   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orgwise_treasury  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orgwise_reporting CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

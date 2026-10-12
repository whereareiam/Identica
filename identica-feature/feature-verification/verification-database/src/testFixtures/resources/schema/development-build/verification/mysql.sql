-- The verification tables a development build of Identica 3.0.0 created on MYSQL.
-- Generated from the hand-written DDL of commit 13af5c2f684c1d824a8ea06f01186a0b941cdd36 (origin/dev).
-- Dialectica 1.0.2 ran each block below as one JDBC statement; run them the same way.
-- statement
CREATE TABLE IF NOT EXISTS identica_verification_enrollments (
	unique_id CHAR(36) NOT NULL,
	method_id VARCHAR(64) NOT NULL,
	enrollment_id VARCHAR(64) NOT NULL,
	credential TEXT NOT NULL,
	label VARCHAR(64),
	created_at BIGINT,
	enabled_at BIGINT,
	PRIMARY KEY (unique_id, method_id, enrollment_id),
	FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
)
-- statement
CREATE TABLE IF NOT EXISTS identica_verification_recovery_codes (
	unique_id CHAR(36) NOT NULL,
	method_id VARCHAR(64) NOT NULL,
	code_hash VARCHAR(128) NOT NULL,
	created_at BIGINT,
	used_at BIGINT,
	PRIMARY KEY (unique_id, method_id, code_hash),
	FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
)
-- statement
CREATE TABLE IF NOT EXISTS identica_verification_selections (
	unique_id CHAR(36) NOT NULL,
	provider_id VARCHAR(64) NOT NULL,
	method_id VARCHAR(64) NOT NULL,
	selected_at BIGINT,
	PRIMARY KEY (unique_id, provider_id),
	FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
)

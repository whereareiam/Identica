-- The verification tables Identica 2.2.1 created on SQLITE, with rows an installation of it could hold.
-- Generated from the hand-written DDL of tag 2.2.1 (commit 145560a92ea99d248d256f6d22167dc668aedb93).
-- Each block below runs as one JDBC statement, as Dialectica ran it then.
-- statement
CREATE TABLE IF NOT EXISTS identica_verification_enrollments (
	unique_id TEXT NOT NULL,
	method_id VARCHAR(64) NOT NULL,
	enrollment_id VARCHAR(64) NOT NULL,
	credential TEXT NOT NULL,
	label VARCHAR(64),
	created_at INTEGER,
	enabled_at INTEGER,
	PRIMARY KEY (unique_id, method_id, enrollment_id),
	FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
)
-- statement
CREATE TABLE IF NOT EXISTS identica_verification_recovery_codes (
	unique_id TEXT NOT NULL,
	method_id VARCHAR(64) NOT NULL,
	code_hash VARCHAR(128) NOT NULL,
	created_at INTEGER,
	used_at INTEGER,
	PRIMARY KEY (unique_id, method_id, code_hash),
	FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
)
-- statement
CREATE TABLE IF NOT EXISTS identica_verification_selections (
	unique_id TEXT NOT NULL,
	provider_id VARCHAR(64) NOT NULL,
	method_id VARCHAR(64) NOT NULL,
	selected_at INTEGER,
	PRIMARY KEY (unique_id, provider_id),
	FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
)

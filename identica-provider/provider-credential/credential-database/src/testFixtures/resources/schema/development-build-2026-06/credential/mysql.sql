-- The credential tables development builds of Identica 3.0.0 created on MYSQL from commit 01c65c0e (June 8) up to e605d1ba (June 13),
-- which still kept the credential provider's migrations in dialectica_schema_migrations, as 2.2.1 did.
-- Generated from the hand-written DDL of commit e605d1ba8e7737a7f8ff27b2345bd7c0896c8776 (origin/dev).
-- Each block below runs as one JDBC statement, as Dialectica ran it then.
-- statement
CREATE TABLE IF NOT EXISTS identica_provider_credential_accounts (
	provider_id VARCHAR(64) NOT NULL,
	provider_subject VARCHAR(128) NOT NULL,
	password_hash VARCHAR(512) NOT NULL,
	hashing_method VARCHAR(64) NOT NULL,
	created_at BIGINT,
	updated_at BIGINT,
	PRIMARY KEY (provider_id, provider_subject)
)
-- statement
CREATE TABLE IF NOT EXISTS identica_provider_credential_accounts_history (
	provider_id VARCHAR(64) NOT NULL,
	provider_subject VARCHAR(128) NOT NULL,
	hashing_method VARCHAR(64) NOT NULL,
	change_reason VARCHAR(32) NOT NULL,
	changed_at BIGINT,
	PRIMARY KEY (provider_id, provider_subject, changed_at),
	FOREIGN KEY (provider_id, provider_subject)
		REFERENCES identica_provider_credential_accounts(provider_id, provider_subject)
		ON DELETE CASCADE
)
-- statement
CREATE TABLE IF NOT EXISTS dialectica_schema_migrations (
	scope VARCHAR(128) NOT NULL,
	version INT NOT NULL,
	name VARCHAR(255) NOT NULL,
	applied_at BIGINT NOT NULL,
	PRIMARY KEY (scope, version)
)
-- statement
INSERT INTO dialectica_schema_migrations (scope, version, name, applied_at) VALUES ('credential-accounts', 1, 'rename_cracked_tables', 900)
-- statement
INSERT INTO dialectica_schema_migrations (scope, version, name, applied_at) VALUES ('credential-accounts', 2, 'rename_cracked_provider_id', 900)

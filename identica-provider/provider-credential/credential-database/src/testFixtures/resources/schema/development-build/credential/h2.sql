-- The credential tables a development build of Identica 3.0.0 created on H2.
-- Generated from the hand-written DDL of commit 13af5c2f684c1d824a8ea06f01186a0b941cdd36 (origin/dev).
-- Dialectica 1.0.2 ran each block below as one JDBC statement; run them the same way.
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

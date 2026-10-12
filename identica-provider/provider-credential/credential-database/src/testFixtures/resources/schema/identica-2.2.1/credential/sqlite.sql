-- The credential tables Identica 2.2.1 created on SQLITE, with rows an installation of it could hold.
-- Generated from the hand-written DDL of tag 2.2.1 (commit 145560a92ea99d248d256f6d22167dc668aedb93).
-- Upgrader and Alice, accounts of the account layout, get their credential links and profiles here.
-- dialectica_schema_migrations is the table Dialectica (dev-62409af) kept the credential provider's migrations in;
-- both of them had run, so passwords of the cracked provider of 1.x are already credential's.
-- Each block below runs as one JDBC statement, as Dialectica ran it then.
-- statement
CREATE TABLE IF NOT EXISTS identica_provider_credential_accounts (
	provider_id VARCHAR(64) NOT NULL,
	provider_subject VARCHAR(128) NOT NULL,
	password_hash VARCHAR(512) NOT NULL,
	hashing_method VARCHAR(64) NOT NULL,
	created_at INTEGER,
	updated_at INTEGER,
	PRIMARY KEY (provider_id, provider_subject)
)
-- statement
CREATE TABLE IF NOT EXISTS identica_provider_credential_accounts_history (
	provider_id VARCHAR(64) NOT NULL,
	provider_subject VARCHAR(128) NOT NULL,
	hashing_method VARCHAR(64) NOT NULL,
	change_reason VARCHAR(32) NOT NULL,
	changed_at INTEGER,
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
INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary, linked_at, last_seen_at) VALUES ('credential', 'dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69', 'dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69', TRUE, 1000, 2000)
-- statement
INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES ('credential', 'dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69', 'Upgrader')
-- statement
INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary, linked_at, last_seen_at) VALUES ('credential', '10920508-d5d8-3eed-93d2-92f193afe7d7', '10920508-d5d8-3eed-93d2-92f193afe7d7', TRUE, 1000, 2000)
-- statement
INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES ('credential', '10920508-d5d8-3eed-93d2-92f193afe7d7', 'Alice')
-- statement
INSERT INTO identica_provider_credential_accounts (provider_id, provider_subject, password_hash, hashing_method, created_at, updated_at) VALUES ('credential', 'dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69', '$2a$12$PmVAazF1bucN9B1.0J/Pl.NfXkoT.fm.y73Rl8otGtpUFyUI3axje', 'bcrypt', 1000, 1000)
-- statement
INSERT INTO identica_provider_credential_accounts_history (provider_id, provider_subject, hashing_method, change_reason, changed_at) VALUES ('credential', 'dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69', 'bcrypt', 'REGISTER', 1000)
-- statement
INSERT INTO identica_provider_credential_accounts (provider_id, provider_subject, password_hash, hashing_method, created_at, updated_at) VALUES ('credential', '10920508-d5d8-3eed-93d2-92f193afe7d7', '$2a$12$g4eqc36wFMELgiLCr0LzWe0dy6MDVxrTcXQGN15cRjYf2LfVzHhb6', 'bcrypt', 1000, 1000)
-- statement
INSERT INTO identica_provider_credential_accounts_history (provider_id, provider_subject, hashing_method, change_reason, changed_at) VALUES ('credential', '10920508-d5d8-3eed-93d2-92f193afe7d7', 'bcrypt', 'REGISTER', 1000)
-- statement
INSERT INTO dialectica_schema_migrations (scope, version, name, applied_at) VALUES ('credential-accounts', 1, 'rename_cracked_tables', 900)
-- statement
INSERT INTO dialectica_schema_migrations (scope, version, name, applied_at) VALUES ('credential-accounts', 2, 'rename_cracked_provider_id', 900)

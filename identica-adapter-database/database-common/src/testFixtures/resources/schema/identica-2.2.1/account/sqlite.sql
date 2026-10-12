-- The account tables Identica 2.2.1 created on SQLITE, with rows an installation of it could hold.
-- Generated from the hand-written DDL of tag 2.2.1 (commit 145560a92ea99d248d256f6d22167dc668aedb93).
-- Each block below runs as one JDBC statement, as Dialectica ran it then.
-- statement
CREATE TABLE IF NOT EXISTS identica_accounts (
	unique_id TEXT PRIMARY KEY,
	username VARCHAR(64) NOT NULL,
	username_source VARCHAR(32) NOT NULL,
	created_at INTEGER,
	last_seen_at INTEGER
)
-- statement
CREATE TABLE IF NOT EXISTS identica_provider_links (
	provider_id VARCHAR(64) NOT NULL,
	provider_subject VARCHAR(128) NOT NULL,
	unique_id TEXT NOT NULL,
	is_primary INTEGER NOT NULL,
	linked_at INTEGER,
	last_seen_at INTEGER,
	PRIMARY KEY (provider_id, provider_subject),
	UNIQUE (unique_id, provider_id),
	FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
)
-- statement
CREATE TABLE IF NOT EXISTS identica_provider_profiles (
	provider_id VARCHAR(64) NOT NULL,
	provider_subject VARCHAR(128) NOT NULL,
	provider_username VARCHAR(64) NOT NULL,
	PRIMARY KEY (provider_id, provider_subject),
	FOREIGN KEY (provider_id, provider_subject) REFERENCES identica_provider_links(provider_id, provider_subject) ON DELETE CASCADE
)
-- statement
CREATE TABLE IF NOT EXISTS identica_account_reservations (
	reservation_key VARCHAR(192) PRIMARY KEY,
	unique_id TEXT NOT NULL,
	created_at INTEGER NOT NULL,
	expires_at INTEGER NOT NULL
)
-- statement
CREATE TABLE IF NOT EXISTS identica_username_history (
	unique_id TEXT NOT NULL,
	provider_id VARCHAR(64),
	old_username VARCHAR(64) NOT NULL,
	new_username VARCHAR(64) NOT NULL,
	source VARCHAR(32) NOT NULL,
	changed_at INTEGER NOT NULL
)
-- statement
INSERT INTO identica_accounts (unique_id, username, username_source, created_at, last_seen_at) VALUES ('dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69', 'Upgrader', 'provider', 1000, 2000)
-- statement
INSERT INTO identica_accounts (unique_id, username, username_source, created_at, last_seen_at) VALUES ('10920508-d5d8-3eed-93d2-92f193afe7d7', 'Alice', 'provider', 1000, 2000)
-- statement
INSERT INTO identica_accounts (unique_id, username, username_source, created_at, last_seen_at) VALUES ('faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b', 'Bob', 'manual', 1100, NULL)
-- statement
INSERT INTO identica_accounts (unique_id, username, username_source, created_at, last_seen_at) VALUES ('0af3f783-cbb9-32f0-953c-0d7e29e82d58', 'Carol', 'system', NULL, NULL)
-- statement
INSERT INTO identica_accounts (unique_id, username, username_source, created_at, last_seen_at) VALUES ('80333097-598c-3d5f-9b99-4ef1a3920f06', 'Dave', 'provider', 1200, 2200)
-- statement
INSERT INTO identica_accounts (unique_id, username, username_source, created_at, last_seen_at) VALUES ('c2583fbf-5ec9-3d18-b0b1-9b4ceaff58d6', 'dave', 'manual', 1300, 2300)
-- statement
INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary, linked_at, last_seen_at) VALUES ('example', 'faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b', 'faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b', TRUE, 1000, 2000)
-- statement
INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES ('example', 'faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b', 'Bob')
-- statement
INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary, linked_at, last_seen_at) VALUES ('example', '0af3f783-cbb9-32f0-953c-0d7e29e82d58', '0af3f783-cbb9-32f0-953c-0d7e29e82d58', TRUE, 1000, 2000)
-- statement
INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES ('example', '0af3f783-cbb9-32f0-953c-0d7e29e82d58', 'Carol')
-- statement
INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary, linked_at, last_seen_at) VALUES ('example', '80333097-598c-3d5f-9b99-4ef1a3920f06', '80333097-598c-3d5f-9b99-4ef1a3920f06', TRUE, 1000, 2000)
-- statement
INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES ('example', '80333097-598c-3d5f-9b99-4ef1a3920f06', 'Dave')
-- statement
INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary, linked_at, last_seen_at) VALUES ('example', 'c2583fbf-5ec9-3d18-b0b1-9b4ceaff58d6', 'c2583fbf-5ec9-3d18-b0b1-9b4ceaff58d6', TRUE, 1000, 2000)
-- statement
INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES ('example', 'c2583fbf-5ec9-3d18-b0b1-9b4ceaff58d6', 'dave')
-- statement
INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES ('upgrader', 'dfe90880-73a1-3a0e-a7e7-8cda1bb6cb69', 1000, 4102444800000)
-- statement
INSERT INTO identica_username_history (unique_id, provider_id, old_username, new_username, source, changed_at) VALUES ('10920508-d5d8-3eed-93d2-92f193afe7d7', 'example', 'Alicia', 'Alice', 'provider', 3000)
-- statement
INSERT INTO identica_username_history (unique_id, provider_id, old_username, new_username, source, changed_at) VALUES ('faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b', NULL, 'Bobby', 'Bob', 'manual', 3100)
-- statement
INSERT INTO identica_username_history (unique_id, provider_id, old_username, new_username, source, changed_at) VALUES ('c2583fbf-5ec9-3d18-b0b1-9b4ceaff58d6', 'example', 'Dave', 'dave', 'manual', 3200)
-- statement
INSERT INTO identica_username_history (unique_id, provider_id, old_username, new_username, source, changed_at) VALUES ('00000000-0000-0000-0000-00000000dead', 'example', 'Ghost', 'Ghosty', 'provider', 3300)

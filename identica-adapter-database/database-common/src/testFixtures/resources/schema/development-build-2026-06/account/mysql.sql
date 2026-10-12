-- The account tables development builds of Identica 3.0.0 created on MYSQL, from commit 01c65c0e (June 8) up to 898aac4b (October 11),
-- before the fix of commit 01fdaa5d. Generated from the hand-written DDL of commit 898aac4bb349844494a85109150e1cb36aa4cfad (origin/dev).
-- MySQL rejected the username history block, CREATE TABLE and CREATE INDEX in one statement, so those builds left that table out.
-- Each block below runs as one JDBC statement, as Dialectica ran it then.
-- statement
CREATE TABLE IF NOT EXISTS identica_accounts (
	unique_id CHAR(36) PRIMARY KEY,
	username VARCHAR(64) NOT NULL,
	created_at BIGINT,
	last_seen_at BIGINT
)
-- statement
CREATE TABLE IF NOT EXISTS identica_provider_links (
	provider_id VARCHAR(64) NOT NULL,
	provider_subject VARCHAR(128) NOT NULL,
	unique_id CHAR(36) NOT NULL,
	is_primary BOOLEAN NOT NULL,
	linked_at BIGINT,
	last_seen_at BIGINT,
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
	unique_id CHAR(36) NOT NULL,
	created_at BIGINT NOT NULL,
	expires_at BIGINT NOT NULL
)
-- statement
CREATE TABLE IF NOT EXISTS identica_account_username_state (
	unique_id CHAR(36) NOT NULL,
	source VARCHAR(32) NOT NULL,
	updated_at BIGINT NOT NULL,
	PRIMARY KEY (unique_id),
	FOREIGN KEY (unique_id)
		REFERENCES identica_accounts(unique_id)
		ON DELETE CASCADE
)

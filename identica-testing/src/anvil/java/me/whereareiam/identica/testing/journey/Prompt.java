package me.whereareiam.identica.testing.journey;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Messages Identica and its providers send with their default configuration, identified by a distinctive line.
 */
@Getter
@RequiredArgsConstructor
public enum Prompt {
	PROVIDER_CHOICE("Select an authentication method for account"),
	CREDENTIAL_OFFERED("[Credential]: Register using password."),
	REGISTRATION_PASSWORD("Use /pass [Password] to continue."),
	REGISTRATION_CONFIRMATION("Use /passconfirm [Password] to continue."),
	REGISTERED_WITH_CREDENTIAL("You just registered via credential provider."),
	LOGIN("Use /login [Password] to continue."),
	INVALID_PASSWORD("Invalid password."),
	AUTHENTICATED_WITH_PASSWORD("You were authenticated via password.");

	private final String text;
}

package me.whereareiam.identica.testing.security;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A login in progress belongs to the connection that made it: the username together with the address it came
 * from. A player who returns from the same address continues where she left; a client using her username from
 * another address starts over and leaves her progress as it was.
 */
@Tag("account")
class LoginProgressTest {
	private static final String PASSWORD = "Secret-123";
	private static final String ELSEWHERE = "127.0.0.2";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void continuesAtTheAuthenticatorPromptFromTheSameAddress(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);
		verificationPrompt(alice.journey().rejoin()).leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void asksAnotherAddressForThePasswordAlthoughTheOwnerTypedIt(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);
		Journey owner = verificationPrompt(alice.journey().rejoin()).leave();

		Journey.of(anvil, Client.offline("Alice").from(ELSEWHERE)).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.doesNotSee(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.leave();

		owner.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void leavesARegistrationInProgressToItsOwner(ScenarioContext anvil) {
		Journey owner = registrationPrompt(Journey.offline(anvil, "Bob").join())
				.command("pass " + PASSWORD)
				.sees(m -> m.credential().getScenario().getRegistration().getConfirmPrompt())
				.leave();

		registrationPrompt(Journey.of(anvil, Client.offline("Bob").from(ELSEWHERE)).join())
				.command("passconfirm Wrong-456")
				.doesNotSee(m -> m.credential().getCompletion().getRegistration().getBody())
				.leave();

		owner.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getConfirmPrompt())
				.command("passconfirm " + PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	/**
	 * The proxy refuses a second client with a username that is already connected. Refusing it leaves the player
	 * who holds the username at her prompt, able to log in.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void aRefusedSecondClientLeavesTheConnectedPlayerAtHerPrompt(ScenarioContext anvil) {
		Journey owner = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());

		Journey.of(anvil, Client.offline("Alice").from(ELSEWHERE)).attempt().disconnected();

		owner.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Expects a connection that was just accepted to be asked for a new password, choosing Credential first when the
	 * network asks which provider to register with.
	 */
	private static Journey registrationPrompt(Journey joined) {
		joined.on(AUTH);
		int asked = joined.seesAnyOf(
				m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody(),
				m -> m.credential().getScenario().getRegistration().getPrompt());
		if (asked == 0)
			joined.enroll(Provider.CREDENTIAL).sees(m -> m.credential().getScenario().getRegistration().getPrompt());

		return joined;
	}

	/**
	 * Registers Alice, logs her in and enrolls an authenticator app; she leaves afterwards.
	 */
	private static Enrolled enrolled(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
		Authenticator app = Authenticator.of(alice.command("2fa enroll totp")
				.sees(m -> m.verification().getMethods().getTotp().getPending())
				.reads("secret"));
		alice.command("2fa enroll confirm " + app.code())
				.sees(m -> m.verification().getCommands().getConfirm().getRecoveryCodes().getBody())
				.command("2fa enroll confirm saved")
				.sees(m -> m.verification().getCommands().getConfirm().getEnabled())
				.leave();
		return new Enrolled(alice, app);
	}

	/**
	 * Expects the password prompt of a connection that was just accepted and types the password, which brings
	 * Alice to the authenticator prompt.
	 */
	private static Journey verificationPrompt(Journey joined) {
		return joined.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt());
	}

	private record Enrolled(Journey journey, Authenticator app) {
	}
}

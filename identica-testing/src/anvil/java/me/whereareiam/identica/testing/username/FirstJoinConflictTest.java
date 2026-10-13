package me.whereareiam.identica.testing.username;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.model.conflict.ConflictPartyCondition;
import me.whereareiam.identica.model.conflict.ConflictRuleOutcome;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.identity.username.UsernameHolderOnline;
import me.whereareiam.identica.type.identity.username.UsernameRenameTooLong;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * A premium owner joins for the first time with a username a Credential account holds. Premium owns its players'
 * usernames and Credential players choose theirs, so the default rules give the name to the owner and rename the
 * Credential account, which keeps logging in with its password under its new name. Joining through Premium's
 * hostname picks Premium for the owner.
 */
class FirstJoinConflictTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true)
	void givesTheOwnerTheUsernameAndRenamesTheCredentialAccount(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, credentialClient("Steve"), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, premiumClient(account)).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.playsAs("Steve");

		holder.named("Steve_credential").rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Steve_credential");
		Administrator.at(anvil, PROXY).findsNoDuplicateUsernames();
	}

	/**
	 * A holder who is online keeps playing under the name until leaving; the owner plays this session under a
	 * temporary name and under the name itself once the holder has left.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true)
	void letsAnOnlineHolderKeepPlayingAndGivesTheOwnerATemporaryName(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, credentialClient("Steve"), PASSWORD)
				.rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey owner = Journey.of(anvil, premiumClient(account)).named("Steve_premium").join()
				.on(LOBBY)
				.playsAs("Steve_premium");
		holder.playsAs("Steve");

		owner.leave();
		holder.leave();
		owner.named("Steve").rejoin()
				.on(LOBBY)
				.playsAs("Steve");
		holder.named("Steve_credential").rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Steve_credential");
	}

	/**
	 * A rule may disconnect an online holder instead. The proxy names the owner while the holder is still connected,
	 * so the owner plays this session under a temporary name and under the name from the next join on.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true, configure = KickHolder.class)
	void disconnectsAnOnlineHolderWhenTheRuleSaysSo(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, credentialClient("Steve"), PASSWORD)
				.rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey owner = Journey.of(anvil, premiumClient(account)).named("Steve_premium").join()
				.on(LOBBY)
				.playsAs("Steve_premium");
		holder.kickedWith(m -> m.username().getPipeline().getPolicy().getHolderReplaced());

		owner.leave()
				.named("Steve")
				.rejoin()
				.on(LOBBY)
				.playsAs("Steve");
	}

	/**
	 * A renamed username longer than Minecraft's 16 characters is shortened by default.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true)
	void shortensARenamedUsernameToSixteenCharacters(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, credentialClient("Christopherson"), PASSWORD);
		SessionIdentity account = yggdrasil.register("Christopherson");

		Journey.of(anvil, premiumClient(account)).join()
				.on(LOBBY)
				.playsAs("Christopherson");

		holder.named("Chris_credential").rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Chris_credential");
	}

	/**
	 * With shortening turned off, a renamed username that does not fit refuses the owner, and the holder keeps it.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true, configure = RefuseLongNames.class)
	void refusesTheOwnerWhenTheRenamedUsernameWouldBeTooLong(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, credentialClient("Christopherson"), PASSWORD);
		SessionIdentity account = yggdrasil.register("Christopherson");

		Journey.of(anvil, premiumClient(account)).attempt()
				.kickedWith(m -> m.username().getPipeline().getPolicy().getTaken());

		holder.rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Christopherson");
	}

	/**
	 * A rule naming both providers is more specific than the default rules naming guarantees, and wins.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, entrypoints = true, configure = RefuseBetweenProviders.class)
	void letsARuleNamingProvidersOverrideTheDefaults(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		Journey holder = Accounts.credential(anvil, credentialClient("Steve"), PASSWORD);
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.of(anvil, premiumClient(account)).attempt()
				.kickedWith(m -> m.username().getPipeline().getPolicy().getTaken());

		holder.rejoin()
				.on(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.playsAs("Steve");
	}

	private static Client credentialClient(String username) {
		return Client.offline(username).through(Provider.CREDENTIAL.getEntrypoint());
	}

	private static Client premiumClient(SessionIdentity account) {
		return Client.premium(account).through(Provider.PREMIUM.getEntrypoint());
	}

	static final class KickHolder implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.usernameConflicts().getOutcomes().get("rename-holder").put("holderOnline", UsernameHolderOnline.KICK_HOLDER.name());
		}
	}

	static final class RefuseLongNames implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.usernameConflicts().getOutcomes().get("rename-holder").put("tooLong", UsernameRenameTooLong.REFUSE.name());
		}
	}

	static final class RefuseBetweenProviders implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			UsernameConflictRule rule = new UsernameConflictRule();
			rule.setArriving(providers(Provider.PREMIUM));
			rule.setHolder(providers(Provider.CREDENTIAL));
			rule.setOutcome(ConflictRuleOutcome.of("refuse"));

			List<UsernameConflictRule> rules = new ArrayList<>(proxy.usernameConflicts().getRules());
			rules.add(rule);
			proxy.usernameConflicts().setRules(rules);
		}

		private static ConflictPartyCondition providers(Provider provider) {
			ConflictPartyCondition condition = new ConflictPartyCondition();
			condition.setProviders(List.of(provider.getId()));
			return condition;
		}
	}
}

package me.whereareiam.identica.testing.username;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.CLIENT_PROFILE;
import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.LOOKUP;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * An offline client holds a premium username while it registers with Credential, and the owner of the premium
 * account joins on the same proxy. The owner's premium login proves the username, so the owner takes it over and the
 * client that only claimed it is disconnected. The proxy still knows the other client under the username while the
 * owner connects, so the owner plays this session under a temporary name and the next one under the username.
 */
@Tag("identity")
class ProvenTakeoverTest {
	@Test
	@Identica(configure = ClientProfileDetection.class)
	void letsTheProvenOwnerTakeOverAUsernameAnUnprovenClientHolds(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey holder = Journey.of(anvil, Client.offline("Steve").from("127.0.0.2")).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());

		Journey owner = Journey.premium(anvil, account).named("Steve_premium").join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.playsAs("Steve_premium");

		holder.kickedWith(m -> m.identica().getEngine().getConcurrentLoginKick());

		owner.leave()
				.named("Steve")
				.rejoin()
				.on(LOBBY)
				.playsAs("Steve");
	}

	private static final class ClientProfileDetection implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.premium().getDetection().setMethods(List.of(CLIENT_PROFILE, LOOKUP));
		}
	}
}

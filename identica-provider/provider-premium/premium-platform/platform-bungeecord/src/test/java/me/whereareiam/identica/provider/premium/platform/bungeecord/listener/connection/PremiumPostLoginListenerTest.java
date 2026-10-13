package me.whereareiam.identica.provider.premium.platform.bungeecord.listener.connection;

import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premium Bungee Post Login Listener")
class PremiumPostLoginListenerTest {
	@Mock
	private PremiumProfileStore profileStore;

	@Mock
	private ProviderAttemptStore attemptStore;

	@Mock
	private PrepareStateStore prepareStateStore;

	@Mock
	private PostLoginEvent event;

	@Mock
	private ProxiedPlayer player;

	@Test
	@DisplayName("Stores the prepared premium provider subject instead of the rewritten account UUID")
	void storesPreparedPremiumProviderSubject() {
		UUID accountUniqueId = UUID.randomUUID();
		UUID premiumUniqueId = UUID.randomUUID();
		PremiumPostLoginListener listener = new PremiumPostLoginListener(profileStore, attemptStore, prepareStateStore);
		PrepareDecision prepared = PrepareDecision.builder()
				.status(PrepareDecision.Status.ALLOW)
				.provider(ProviderContext.of(
						PremiumConstants.PROVIDER_ID,
						premiumUniqueId.toString(),
						"PlayerOne"
				))
				.build();

		when(event.getPlayer()).thenReturn(player);
		when(player.getUniqueId()).thenReturn(accountUniqueId);
		when(player.getName()).thenReturn("PlayerOne");
		when(player.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
		when(prepareStateStore.peek(accountUniqueId)).thenReturn(Optional.of(prepared));

		listener.onEvent(event);

		verify(profileStore).save("PlayerOne", premiumUniqueId.toString());
		verify(profileStore, never()).save("PlayerOne", accountUniqueId.toString());
		verify(attemptStore).clearAttempt(
				PremiumConstants.PROVIDER_ID,
				PremiumConstants.ATTEMPT_SCOPE_VERIFY,
				"PlayerOne",
				"127.0.0.1"
		);
	}

	@Test
	@DisplayName("Stores the prepared credential provider subject instead of the rewritten account UUID")
	void storesPreparedCredentialProviderSubject() {
		UUID accountUniqueId = UUID.randomUUID();
		UUID credentialSubject = UUID.randomUUID();
		PremiumPostLoginListener listener = new PremiumPostLoginListener(profileStore, attemptStore, prepareStateStore);
		PrepareDecision prepared = PrepareDecision.builder()
				.status(PrepareDecision.Status.ALLOW)
				.provider(ProviderContext.of(
						"credential",
						credentialSubject.toString(),
						"PlayerOne"
				))
				.build();

		when(event.getPlayer()).thenReturn(player);
		when(player.getUniqueId()).thenReturn(accountUniqueId);
		when(player.getName()).thenReturn("PlayerOne");
		when(player.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
		when(prepareStateStore.peek(accountUniqueId)).thenReturn(Optional.of(prepared));

		listener.onEvent(event);

		verify(profileStore).save("PlayerOne", credentialSubject.toString());
		verify(profileStore, never()).save("PlayerOne", accountUniqueId.toString());
		verify(attemptStore).clearAttempt(
				PremiumConstants.PROVIDER_ID,
				PremiumConstants.ATTEMPT_SCOPE_VERIFY,
				"PlayerOne",
				"127.0.0.1"
		);
	}
}

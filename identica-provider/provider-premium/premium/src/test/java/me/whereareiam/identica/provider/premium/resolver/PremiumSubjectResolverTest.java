package me.whereareiam.identica.provider.premium.resolver;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileSnapshot;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premium Subject Resolver")
class PremiumSubjectResolverTest {
	private static final String USERNAME = "PlayerOne";

	@Mock
	private PremiumProfileStore profileStore;

	@DisplayName("Resolves the UUID of an online login as a verified subject")
	@Test
	void onlineLoginIsVerified() {
		UUID online = UUID.randomUUID();

		SubjectResolution resolution = resolve(online);

		assertNotNull(resolution);
		assertEquals("premium", resolution.getProviderId());
		assertEquals(online.toString(), resolution.getProviderSubject());
		assertTrue(resolution.isVerified());
		verifyNoInteractions(profileStore);
	}

	@DisplayName("Resolves nothing for an offline login")
	@Test
	void offlineLoginIsNotPremium() {
		assertNull(resolve(UniqueIdGenerator.offlinePlayerUniqueId(USERNAME)));
		verifyNoInteractions(profileStore);
	}

	@DisplayName("Resolves a profile only known from the username lookup as an unverified subject")
	@Test
	void lookedUpProfileIsNotVerified() {
		when(profileStore.find(USERNAME)).thenReturn(new PremiumProfileSnapshot("premium-subject", System.currentTimeMillis()));

		SubjectResolution resolution = resolve(null);

		assertNotNull(resolution);
		assertEquals("premium-subject", resolution.getProviderSubject());
		assertFalse(resolution.isVerified());
	}

	private SubjectResolution resolve(UUID observedUniqueId) {
		return new PremiumSubjectResolver(profileStore).resolve(SubjectResolveContext.builder()
				.identity(new ConnectionIdentity(null, observedUniqueId, USERNAME, "127.0.0.1"))
				.build());
	}
}

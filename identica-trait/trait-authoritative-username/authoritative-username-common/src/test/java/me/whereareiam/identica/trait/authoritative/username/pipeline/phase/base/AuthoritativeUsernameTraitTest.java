package me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base;

import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameState;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthoritativeUsernameTraitTest {
	@Test
	void followsTheTraitWithoutAnyFeatureDeclarationAndPreservesManualAuthority() {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("premium");
		descriptor.setTraits(Set.of(ProviderTrait.AUTHORITATIVE_USERNAME));
		assertTrue(descriptor.getSupportedFeatureIds().isEmpty());
		ProviderManager manager = mock(ProviderManager.class);
		when(manager.getProviders()).thenReturn(List.of(InternalProvider.builder().descriptor(descriptor).state(me.whereareiam.identica.type.provider.ProviderState.ENABLED).build()));
		var persistence = mock(AccountUsernameStatePersistenceService.class);
		UUID id = UUID.randomUUID();
		when(persistence.find(id)).thenReturn(Optional.empty());
		var phase = new AbstractUsernamePhase(manager, persistence) {};
		Account account = Account.builder().uniqueId(id).username("OldName").build();
		var link = AccountProviderLink.builder().uniqueId(id).providerSubject("subject").providerId("premium").primaryLink(true).build();
		var profile = AccountProviderProfile.builder().providerId("premium").providerSubject("subject").providerUsername("VerifiedName").build();
		assertTrue(phase.synchronize(account, link, profile));
		assertEquals("VerifiedName", account.getUsername());
		account.setUsername("ManualName");
		when(persistence.find(id)).thenReturn(Optional.of(AccountUsernameState.builder().uniqueId(id).source(AccountUsernameSource.MANUAL).build()));
		assertFalse(phase.synchronize(account, link, profile));
		assertEquals("ManualName", account.getUsername());
		descriptor.setTraits(Set.of());
		assertFalse(phase.synchronize(account, link, profile));
	}
}

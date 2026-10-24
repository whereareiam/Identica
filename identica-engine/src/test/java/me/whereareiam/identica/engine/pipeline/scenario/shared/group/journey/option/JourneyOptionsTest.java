package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option;

import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimRanking;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Journey Options")
class JourneyOptionsTest {
	private final ProviderOperations providerOperations = mock(ProviderOperations.class);
	private final MigrationJourneyRegistry migrationJourneyRegistry = mock(MigrationJourneyRegistry.class);
	private final Providers providers = new Providers();
	private MigrationContext context;

	@BeforeEach
	void offerWebsite() {
		context = MigrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.targetProviderId("website")
				.build();
		when(providerOperations.eligibleProviders(context, PipelineType.MIGRATION))
				.thenReturn(List.of(provider("website"), provider("credential")));
	}

	@DisplayName("Offers a provider without methods as one option")
	@Test
	void providerWithoutMethodsIsOneOption() {
		assertEquals(List.of("website"), ids(null));
	}

	@DisplayName("Offers the target's methods in the provider's order, and no other provider in a migration")
	@Test
	void offersTheMethodsInTheProvidersOrder() {
		websiteMethods(method("quick"), method("guided"));

		assertEquals(List.of("website:quick", "website:guided"), ids(null));
	}

	@DisplayName("Follows the order and the selection of the provider's entry")
	@Test
	void followsTheProviderEntry() {
		websiteMethods(method("quick"), method("guided"), method("assisted"));
		Providers.ProviderEntry entry = new Providers.ProviderEntry();
		entry.setId("website");
		Providers.ProviderEntry.Methods methods = new Providers.ProviderEntry.Methods();
		methods.setMigration(List.of("guided", "quick"));
		entry.setMethods(methods);
		providers.getProviders().add(entry);

		assertEquals(List.of("website:guided", "website:quick"), ids(null));
	}

	@DisplayName("Leaves out a method that is not available or that the journey excluded")
	@Test
	void leavesOutUnavailableAndExcludedMethods() {
		websiteMethods(
				method("quick").toBuilder().availability(ignored -> false).build(),
				method("guided"),
				method("assisted")
		);
		JourneyOverrideItem override = new JourneyOverrideItem(null, -1, false, null, List.of(), List.of("website:assisted"));

		assertEquals(List.of("website:guided"), ids(override));
	}

	@DisplayName("Puts the method the journey already runs first")
	@Test
	void putsTheRunningMethodFirst() {
		websiteMethods(method("quick"), method("guided"));
		ProviderContext provider = ProviderContext.of("website", null, "PlayerOne");
		provider.setMethod("guided");
		context.setProvider(provider);

		assertEquals(List.of("website:guided", "website:quick"), ids(null));
	}

	private List<String> ids(JourneyOverrideItem override) {
		JourneyOptions options = new JourneyOptions(
				providerOperations,
				new DefaultProviderSelector(new ProviderClaimRanking(mock(ProviderManager.class))),
				() -> mock(AuthenticationJourneyRegistry.class),
				() -> mock(RegistrationJourneyRegistry.class),
				() -> migrationJourneyRegistry,
				() -> providers
		);

		return options.resolve(context, PipelineType.MIGRATION, override).stream()
				.map(JourneyOption::id)
				.toList();
	}

	private void websiteMethods(ProviderMethod... methods) {
		when(migrationJourneyRegistry.methods("website")).thenReturn(List.of(methods));
	}

	private ProviderMethod method(String name) {
		return ProviderMethod.builder()
				.providerId("website")
				.name(name)
				.scenario(PipelineType.MIGRATION)
				.build();
	}

	private InternalProvider provider(String id) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		descriptor.setName(id);
		descriptor.setVersion("1.0.0");
		descriptor.setMain("ignored.Main");
		descriptor.setSupportedPlatforms(List.of("ANY"));
		return InternalProvider.builder().descriptor(descriptor).state(ProviderState.ENABLED).build();
	}
}

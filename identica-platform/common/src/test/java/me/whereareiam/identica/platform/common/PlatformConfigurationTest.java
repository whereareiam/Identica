package me.whereareiam.identica.platform.common;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.platform.common.adapter.PlatformHandshakeDecisionAdapter;
import me.whereareiam.identica.platform.common.adapter.PlatformLoginDecisionAdapter;
import me.whereareiam.identica.platform.common.adapter.PlatformProfileAdapter;
import me.whereareiam.identica.platform.common.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.handshake.HandshakeApplier;
import me.whereareiam.identica.handshake.HandshakeApplierRegistry;
import me.whereareiam.identica.handshake.HandshakeContext;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.platform.adapter.PlatformForcedHostAdapter;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierContributor;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierRegistry;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Platform Configuration")
class PlatformConfigurationTest {
	private final Injector injector = Guice.createInjector(new TestConfiguration());

	@DisplayName("An adapter is injected under its contract with the event type its class declares")
	@Test
	void adapterIsBoundUnderItsDeclaredContract() {
		assertInstanceOf(TestHandshakeDecision.class, injector.getInstance(Key.get(new TypeLiteral<PlatformHandshakeDecisionAdapter<HandshakeEvent>>() {})));
		assertInstanceOf(TestLoginDecision.class, injector.getInstance(Key.get(new TypeLiteral<PlatformLoginDecisionAdapter<LoginEvent>>() {})));
		assertInstanceOf(TestResumeDecision.class, injector.getInstance(Key.get(new TypeLiteral<PlatformResumeDecisionAdapter<LoginEvent>>() {})));
		assertInstanceOf(TestProfile.class, injector.getInstance(Key.get(new TypeLiteral<PlatformProfileAdapter<LoginEvent>>() {})));
		assertInstanceOf(TestDelivery.class, injector.getInstance(PlatformDeliveryAdapter.class));
		assertInstanceOf(TestForcedHost.class, injector.getInstance(PlatformForcedHostAdapter.class));
	}

	@DisplayName("The handshake applier registry answers as one instance under both of its contracts")
	@Test
	void handshakeApplierRegistryIsOneInstance() {
		PlatformHandshakeApplierRegistry<TestContext> platform = injector.getInstance(Key.get(new TypeLiteral<>() {}));
		HandshakeApplierRegistry<TestContext> plain = injector.getInstance(Key.get(new TypeLiteral<>() {}));

		assertInstanceOf(TestRegistry.class, platform);
		assertSame(platform, plain);
	}

	@DisplayName("Handshake applier contributors are an empty set until a provider contributes")
	@Test
	void handshakeApplierContributorsStartEmpty() {
		Set<PlatformHandshakeApplierContributor<TestContext>> contributors = injector.getInstance(Key.get(new TypeLiteral<>() {}));

		assertTrue(contributors.isEmpty());
	}

	@DisplayName("The platform's own bindings are configured after its adapters")
	@Test
	void platformBindingsAreConfigured() {
		assertSame(TestConfiguration.MARKER, injector.getInstance(String.class));
	}

	private static final class TestConfiguration extends PlatformConfiguration<TestContext> {
		private static final String MARKER = "platform";

		@Override
		protected @NotNull Class<TestDelivery> delivery() {
			return TestDelivery.class;
		}

		@Override
		protected @NotNull Class<TestForcedHost> forcedHost() {
			return TestForcedHost.class;
		}

		@Override
		protected @NotNull Class<TestRegistry> handshakeApplierRegistry() {
			return TestRegistry.class;
		}

		@Override
		protected @NotNull Class<TestHandshakeDecision> handshakeDecision() {
			return TestHandshakeDecision.class;
		}

		@Override
		protected @NotNull Class<TestLoginDecision> loginDecision() {
			return TestLoginDecision.class;
		}

		@Override
		protected @NotNull Class<TestResumeDecision> resumeDecision() {
			return TestResumeDecision.class;
		}

		@Override
		protected @NotNull Class<TestProfile> profile() {
			return TestProfile.class;
		}

		@Override
		protected void configurePlatform() {
			bind(String.class).toInstance(MARKER);
		}
	}

	private interface TestContext extends HandshakeContext {
	}

	private record HandshakeEvent() {
	}

	private record LoginEvent() {
	}

	private static final class TestDelivery implements PlatformDeliveryAdapter {
		@Override
		public @NotNull DeliveryCheckpoint initialPromptCheckpoint() {
			return DeliveryCheckpoint.values()[0];
		}

		@Override
		public void armInitialReady(@NotNull Identity identity, @Nullable String currentServer) {
		}

		@Override
		public void clear(@NotNull UUID connectionUniqueId) {
		}
	}

	private static final class TestForcedHost implements PlatformForcedHostAdapter {
		@Override
		public @NotNull Optional<String> resolve(@NotNull ConnectionIdentity connection) {
			return Optional.empty();
		}
	}

	@Singleton
	private static final class TestRegistry implements PlatformHandshakeApplierRegistry<TestContext> {
		@Override
		public void register(@NotNull HandshakeApplier<TestContext> applier) {
		}

		@Override
		public void unregister(@NotNull HandshakeApplier<TestContext> applier) {
		}

		@Override
		public void applyAll(@NotNull TestContext context, @NotNull HandshakeInstruction instruction) {
		}
	}

	private static final class TestHandshakeDecision implements PlatformHandshakeDecisionAdapter<HandshakeEvent> {
		@Override
		public @NotNull CompletionStage<Void> process(@NotNull HandshakeEvent event) {
			return CompletableFuture.completedFuture(null);
		}
	}

	private static final class TestLoginDecision implements PlatformLoginDecisionAdapter<LoginEvent> {
		@Override
		public void process(@NotNull LoginEvent event) {
		}
	}

	private static final class TestResumeDecision implements PlatformResumeDecisionAdapter<LoginEvent> {
		@Override
		public void resume(@NotNull LoginEvent event) {
		}
	}

	private static final class TestProfile implements PlatformProfileAdapter<LoginEvent> {
		@Override
		public void apply(@NotNull LoginEvent event) {
		}
	}
}

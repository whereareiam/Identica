package me.whereareiam.identica.engine.completion;

import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.completion.CompletionPipeline;
import me.whereareiam.identica.engine.pipeline.completion.CompletionPipelineRegistry;
import me.whereareiam.identica.engine.pipeline.completion.group.context.CompletionContextGroup;
import me.whereareiam.identica.engine.pipeline.completion.group.context.phase.BuildCompletionContextPhase;
import me.whereareiam.identica.engine.pipeline.completion.group.context.phase.ResolveCompletionProviderPhase;
import me.whereareiam.identica.engine.pipeline.completion.group.context.phase.ResolveCompletionSessionPhase;
import me.whereareiam.identica.engine.pipeline.completion.group.step.CompletionStepGroup;
import me.whereareiam.identica.engine.pipeline.completion.group.step.phase.ExecuteCompletionStepsPhase;
import me.whereareiam.identica.model.pipeline.completion.CompletionContext;
import me.whereareiam.identica.model.pipeline.completion.CompletionPendingState;
import me.whereareiam.identica.pipeline.completion.CompletionPendingStore;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtensionRegistry;
import me.whereareiam.identica.pipeline.completion.step.CompletionStep;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderState;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Completion Pipeline")
class CompletionPipelineTest {
	@DisplayName("Completion pipelines resolve the session, provider, and completion steps before execution")
	@Test
	void consumeAndExecuteResolvesSessionAndProviderSteps() {
		CompletionPendingStore pendingStore = mock(CompletionPendingStore.class);
		CompletionExtensionRegistry extensionRegistry = mock(CompletionExtensionRegistry.class);
		SessionService sessionService = mock(SessionService.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		CompletionPipeline pipeline = new CompletionPipeline(
				pendingStore,
				registry(sessionService, providerManager, extensionRegistry),
				new PipelineExecutor()
		);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID identicaUniqueId = UUID.randomUUID();
		TestIdentity identity = new TestIdentity(connectionUniqueId, "PlayerOne");
		CompletionPendingState pendingState = CompletionPendingState.builder()
				.pipelineType(PipelineType.MIGRATION)
				.connectionUniqueId(connectionUniqueId)
				.identicaUniqueId(identicaUniqueId)
				.sessionReused(true)
				.build();
		Session session = Session.builder()
				.uniqueId(identicaUniqueId)
				.providerId("password")
				.providerSubject("player-one")
				.originalUsername("PlayerOne")
				.effectiveUsername("PlayerOne")
				.build();
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("password");
		InternalProvider provider = InternalProvider.builder()
				.descriptor(descriptor)
				.state(ProviderState.ENABLED)
				.build();
		CompletionStep step = mock(CompletionStep.class);

		when(pendingStore.consume(connectionUniqueId)).thenReturn(Optional.of(pendingState));
		when(sessionService.findByUniqueId(identicaUniqueId)).thenReturn(CompletableFuture.completedFuture(Optional.of(session)));
		when(providerManager.getProviders()).thenReturn(List.of(provider));
		when(extensionRegistry.resolve("password", PipelineType.MIGRATION)).thenReturn(List.of(step));
		when(step.shouldExecute(org.mockito.ArgumentMatchers.any())).thenReturn(true);
		when(step.getName()).thenReturn("test-step");

		pipeline.complete(identity);

		verify(step).execute(argThat((CompletionContext context) ->
				context.getIdentity() == identity
						&& context.getPipelineType() == PipelineType.MIGRATION
						&& context.getSession().getProviderId().equals("password")
						&& context.getProvider() == provider
						&& context.isSessionReused()
		));
	}

	private CompletionPipelineRegistry registry(
			SessionService sessionService,
			ProviderManager providerManager,
			CompletionExtensionRegistry extensionRegistry
	) {
		return new CompletionPipelineRegistry(
				new CompletionContextGroup(),
				new CompletionStepGroup(),
				new ResolveCompletionSessionPhase(sessionService),
				new ResolveCompletionProviderPhase(providerManager),
				new BuildCompletionContextPhase(),
				new ExecuteCompletionStepsPhase(extensionRegistry)
		);
	}

	private static final class TestIdentity extends Identity {
		private TestIdentity(UUID uniqueId, String username) {
			super(uniqueId, username);
		}

		@Override
		public void sendMessage(@NonNull Component message) {
		}

		@Override
		public void sendTitle(@NonNull Title title) {
		}

		@Override
		public boolean hasPermission(@NonNull String permission) {
			return true;
		}

		@Override
		public @NonNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NonNull Audience getAudience() {
			return Audience.empty();
		}

		@Override
		public void disconnect(@NonNull Component reason) {
		}
	}
}

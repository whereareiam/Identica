package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.AuthenticationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.MigrationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.RegistrationPipeline;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("Scenario Pipeline Resume Context")
class ScenarioPipelineResumeContextTest {
	@DisplayName("Registration resumes preserve the account UUID from the original context")
	@Test
	void registrationResumePreservesIdenticaUniqueId() {
		UUID connectionId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		TestRegistrationPipeline pipeline = new TestRegistrationPipeline();

		RegistrationContext base = RegistrationContext.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "1.1.1.1"))
				.intendedServer("lobby")
				.build();

		RegistrationContext merged = (RegistrationContext) pipeline.merge(base, ResumeRequest.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(connectionId, "PlayerOne", "2.2.2.2"))
				.intendedServer("auth")
				.build());

		assertEquals(connectionId, merged.getConnectionUniqueId());
		assertEquals(accountUniqueId, merged.getAccountUniqueId(), "resume should keep the account UUID");
	}

	@DisplayName("Authentication resumes preserve the account UUID from the original context")
	@Test
	void authenticationResumePreservesIdenticaUniqueId() {
		UUID connectionId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline();

		AuthContext base = AuthContext.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "1.1.1.1"))
				.intendedServer("lobby")
				.build();

		AuthContext merged = (AuthContext) pipeline.merge(base, ResumeRequest.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(connectionId, "PlayerOne", "2.2.2.2"))
				.intendedServer("auth")
				.build());

		assertEquals(connectionId, merged.getConnectionUniqueId());
		assertEquals(accountUniqueId, merged.getAccountUniqueId(), "resume should keep the account UUID");
	}

	@DisplayName("Authentication state reference keeps UUIDs after state round-trip")
	@Test
	void authenticationStateReferenceKeepsUuidsAfterStateRoundTrip() {
		UUID connectionId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();

		AuthContext context = AuthContext.builder()
				.connectionUniqueId(connectionId)
				.accountUniqueId(accountUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "1.1.1.1"))
				.intendedServer("lobby")
				.build();
		PipelineState state = PipelineState.initial();
		state.setScenario(context);

		ScenarioContext decoded = state.getScenario();
		PipelineStateReference reference = PipelineStateReference.from(decoded);

		assertEquals(PipelineStateReference.of("PlayerOne", "1.1.1.1"), reference);
	}

	@DisplayName("Migration resumes preserve the account UUID from the original context")
	@Test
	void migrationResumePreservesIdenticaUniqueId() {
		UUID connectionId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		TestMigrationPipeline pipeline = new TestMigrationPipeline();

		MigrationContext base = MigrationContext.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "1.1.1.1"))
				.intendedServer("lobby")
				.targetProviderId("premium")
				.build();

		MigrationContext merged = (MigrationContext) pipeline.merge(base, ResumeRequest.builder()
				.connectionUniqueId(connectionId)
				.identity(new ConnectionIdentity(connectionId, "PlayerOne", "2.2.2.2"))
				.intendedServer("auth")
				.build());

		assertEquals(connectionId, merged.getConnectionUniqueId());
		assertEquals(accountUniqueId, merged.getAccountUniqueId(), "resume should keep the account UUID");
	}

	private static final class TestRegistrationPipeline extends RegistrationPipeline {
		private TestRegistrationPipeline() {
			super(
					mock(PipelineRegistry.class),
					Messages::new,
					Engine::new,
					mock(PipelineStateStore.class),
					mock(PipelineExecutor.class),
					mock(ProviderLinkPersistenceService.class)
			);
		}

		private ScenarioContext merge(ScenarioContext base, ResumeRequest request) {
			return super.mergeContext(base, request);
		}
	}

	private static final class TestAuthenticationPipeline extends AuthenticationPipeline {
		private TestAuthenticationPipeline() {
			super(
					mock(PipelineRegistry.class),
					Messages::new,
					Engine::new,
					mock(PipelineStateStore.class),
					mock(PipelineExecutor.class),
					mock(ProviderLinkPersistenceService.class)
			);
		}

		private ScenarioContext merge(ScenarioContext base, ResumeRequest request) {
			return super.mergeContext(base, request);
		}
	}

	private static final class TestMigrationPipeline extends MigrationPipeline {
		private TestMigrationPipeline() {
			super(
					mock(PipelineRegistry.class),
					Messages::new,
					Engine::new,
					mock(PipelineStateStore.class),
					mock(PipelineExecutor.class)
			);
		}

		private ScenarioContext merge(ScenarioContext base, ResumeRequest request) {
			return super.mergeContext(base, request);
		}
	}
}

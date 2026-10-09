package me.whereareiam.identica.trait.authoritative.username.conflict.resolver;

import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipant;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipantRole;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Format Username Conflict Resolver")
class FormatUsernameConflictResolverTest {
	@DisplayName("Target both emits incoming and existing effects")
	@Test
	void targetBothEmitsIncomingAndExistingEffects() {
		ProviderOperations operations = mock(ProviderOperations.class);
		when(operations.displayProviderName("premium")).thenReturn("Premium");
		when(operations.displayProviderName("credential")).thenReturn("Credential");

		UsernameFormatResolver resolver = new UsernameFormatResolver(operations);
		UsernameFormatResolver.Config config = new UsernameFormatResolver.Config();
		UsernameFormatResolver.Config.Format format = new UsernameFormatResolver.Config.Format();
		format.setPattern("{username}_{incomingProvider}");
		config.setFormat(format);
		config.setTarget("both");

		ConflictResolution resolution = resolver.resolve(context(), config);
		assertEquals(ConflictResolution.Decision.ALLOW, resolution.getDecision());
		assertEquals(
				"Player_Premium",
				resolution.getEffect(UsernameConflictSchema.EFFECT_INCOMING_EFFECTIVE_USERNAME, String.class)
		);
		assertEquals(
				"Player_Premium",
				resolution.getEffect(UsernameConflictSchema.EFFECT_EXISTING_EFFECTIVE_USERNAME, String.class)
		);
	}

	@DisplayName("Existing target only emits existing effect")
	@Test
	void existingTargetOnlyEmitsExistingEffect() {
		ProviderOperations operations = mock(ProviderOperations.class);
		when(operations.displayProviderName("premium")).thenReturn("Premium");
		when(operations.displayProviderName("credential")).thenReturn("Credential");

		UsernameFormatResolver resolver = new UsernameFormatResolver(operations);
		UsernameFormatResolver.Config config = new UsernameFormatResolver.Config();
		UsernameFormatResolver.Config.Format format = new UsernameFormatResolver.Config.Format();
		format.setPattern("{username}_{existingProvider}");
		config.setFormat(format);
		config.setTarget("existing");

		ConflictResolution resolution = resolver.resolve(context(), config);
		assertNull(resolution.getEffect(UsernameConflictSchema.EFFECT_INCOMING_EFFECTIVE_USERNAME, String.class));
		assertEquals(
				"Player_Credential",
				resolution.getEffect(UsernameConflictSchema.EFFECT_EXISTING_EFFECTIVE_USERNAME, String.class)
		);
	}

	private ConflictContext context() {
		ConflictContext context = ConflictContext.builder()
				.key("username")
				.hook("prepare")
				.build();
		context.putAttribute(UsernameConflictSchema.ATTRIBUTE_CANDIDATE_USERNAME, "Player");
		context.putParticipant(participant(UsernameConflictSchema.ROLE_INCOMING, "premium"));
		context.putParticipant(participant(UsernameConflictSchema.ROLE_EXISTING, "credential"));
		return context;
	}

	private ConflictParticipant participant(
			ConflictParticipantRole role,
			String providerId
	) {
		ConflictParticipant participant = ConflictParticipant.builder()
				.role(role)
				.build();
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID, providerId);
		return participant;
	}
}

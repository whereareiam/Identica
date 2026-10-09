package me.whereareiam.identica.trait.authoritative.username.service;

import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import me.whereareiam.identica.trait.authoritative.username.conflict.UsernameConflictType;
import me.whereareiam.identica.trait.authoritative.username.conflict.resolver.UsernameConflictResolver;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictResult;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictSubject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("Username Conflict Resolver")
class AuthoritativeUsernameConflictResolverTest {
	@DisplayName("Applies incoming and existing effects before denying")
	@Test
	void appliesEffectsBeforeDenying() {
		ConflictService conflictService = mock(ConflictService.class);
		UsernameConflictType conflictType = mock(UsernameConflictType.class);
		SessionService sessionService = mock(SessionService.class);

		ConflictContext context = ConflictContext.builder()
				.key("username")
				.hook("prepare")
				.build();
		java.util.UUID uniqueId = java.util.UUID.randomUUID();
		me.whereareiam.identica.model.conflict.participant.ConflictParticipant existing =
				me.whereareiam.identica.model.conflict.participant.ConflictParticipant.builder()
						.role(UsernameConflictSchema.ROLE_EXISTING)
						.build();
		existing.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_ACCOUNT_UNIQUE_ID, uniqueId);
		context.putParticipant(existing);
		when(conflictType.createContext(any())).thenReturn(context);
		when(conflictService.resolve(context)).thenReturn(ConflictResolution.deny(null)
				.withEffect(UsernameConflictSchema.EFFECT_CLOSE_EXISTING, true)
				.withEffect(UsernameConflictSchema.EFFECT_EXISTING_EFFECTIVE_USERNAME, "Existing_1")
				.withEffect(UsernameConflictSchema.EFFECT_INCOMING_EFFECTIVE_USERNAME, "Incoming_1"));
		when(sessionService.close(uniqueId)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
		when(sessionService.findByUniqueId(uniqueId)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()));

		UsernameConflictResolver resolver = new UsernameConflictResolver(
				conflictService,
				conflictType,
				sessionService,
				this::messages
		);

		UsernameConflictResult result = resolver.handle(
				UsernameConflictSubject.builder().build(),
				"Player"
		);

		assertTrue(result.isDenied());
		assertEquals("denied", result.getDenialMessage());
		assertEquals("Incoming_1", result.getEffectiveUsername());
		verify(sessionService).close(uniqueId);
		verify(sessionService).findByUniqueId(uniqueId);
	}

	private AuthoritativeUsernameMessages messages() {
		AuthoritativeUsernameMessages.Pipeline.Prepare prepare = new AuthoritativeUsernameMessages.Pipeline.Prepare();
		prepare.setFailed(java.util.List.of("failed"));
		AuthoritativeUsernameMessages.Pipeline.Identity identity = new AuthoritativeUsernameMessages.Pipeline.Identity();
		identity.setSynchronizationFailed(java.util.List.of("sync"));
		AuthoritativeUsernameMessages.Pipeline.Policy policy = new AuthoritativeUsernameMessages.Pipeline.Policy();
		policy.setPersistenceFailed(java.util.List.of("persist"));
		policy.setConflictDenied(java.util.List.of("denied"));
		policy.setEntrypointRequired(java.util.List.of("entrypoint"));

		AuthoritativeUsernameMessages.Pipeline pipeline = new AuthoritativeUsernameMessages.Pipeline();
		pipeline.setPrepare(prepare);
		pipeline.setIdentity(identity);
		pipeline.setPolicy(policy);

		AuthoritativeUsernameMessages messages = new AuthoritativeUsernameMessages();
		messages.setPipeline(pipeline);
		return messages;
	}
}

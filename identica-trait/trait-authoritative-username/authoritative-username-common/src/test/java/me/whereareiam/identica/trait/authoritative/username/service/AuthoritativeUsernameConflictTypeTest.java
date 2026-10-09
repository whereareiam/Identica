package me.whereareiam.identica.trait.authoritative.username.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.config.provider.Conflicts;
import me.whereareiam.identica.trait.authoritative.username.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipant;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipantRole;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import me.whereareiam.identica.trait.authoritative.username.conflict.UsernameConflictType;
import me.whereareiam.identica.trait.authoritative.username.conflict.UsernameEntrypointGuard;
import me.whereareiam.identica.trait.authoritative.username.conflict.factory.UsernameConflictContextFactory;
import me.whereareiam.identica.trait.authoritative.username.conflict.resolver.UsernameFormatResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Authoritative Username Conflict Type")
class AuthoritativeUsernameConflictTypeTest {
	@DisplayName("Provider pair matching is unordered")
	@Test
	void matchesProviderPairRegardlessOfOrder() {
		UsernameConflictType type = new UsernameConflictType(
				mock(UsernameConflictContextFactory.class),
				mock(UsernameFormatResolver.class),
				mock(UsernameEntrypointGuard.class),
				mock(UsernameConflictsProvider.class)
		);

		ConflictContext context = ConflictContext.builder()
				.key("username")
				.hook("prepare")
				.build();
		context.putParticipant(participant(UsernameConflictSchema.ROLE_INCOMING, "premium"));
		context.putParticipant(participant(UsernameConflictSchema.ROLE_EXISTING, "credential"));

		com.fasterxml.jackson.databind.node.ObjectNode matching = JsonNodeFactory.instance.objectNode();
		matching.putArray("providers")
				.add("credential")
				.add("premium");
		assertTrue(type.matchesRule(context, matching));

		com.fasterxml.jackson.databind.node.ObjectNode nonMatching = JsonNodeFactory.instance.objectNode();
		nonMatching.putArray("providers")
				.add("premium")
				.add("offline");
		assertFalse(type.matchesRule(context, nonMatching));
	}

	@Test
	void readsFeatureOwnedRulesAgainAfterReload() {
		UsernameConflictsProvider provider = mock(UsernameConflictsProvider.class);
		Conflicts initial = new Conflicts();
		Conflicts.ConflictRules initialRules = new Conflicts.ConflictRules();
		initial.getRules().put("username", initialRules);
		Conflicts reloaded = new Conflicts();
		Conflicts.ConflictRules reloadedRules = new Conflicts.ConflictRules();
		reloaded.getRules().put("username", reloadedRules);
		when(provider.get()).thenReturn(initial, reloaded);
		UsernameConflictType type = new UsernameConflictType(
				mock(UsernameConflictContextFactory.class),
				mock(UsernameFormatResolver.class),
				mock(UsernameEntrypointGuard.class),
				provider
		);

		assertSame(initialRules, type.getDefaultRules());
		assertSame(reloadedRules, type.getDefaultRules());
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

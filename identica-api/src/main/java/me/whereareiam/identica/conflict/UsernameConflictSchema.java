package me.whereareiam.identica.conflict;

import me.whereareiam.identica.model.conflict.ConflictAttributeKey;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipantRole;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;

import java.util.UUID;

/**
 * Typed attributes, participant roles, and resolution effects used by username conflicts.
 */
public final class UsernameConflictSchema {
	public static final ConflictParticipantRole ROLE_INCOMING = ConflictParticipantRole.of("incoming");
	public static final ConflictParticipantRole ROLE_EXISTING = ConflictParticipantRole.of("existing");

	public static final ConflictAttributeKey<String> ATTRIBUTE_CANDIDATE_USERNAME =
			ConflictAttributeKey.string("candidateUsername");
	/**
	 * Provider selection of the arriving connection: which provider handles it, and why.
	 */
	public static final ConflictAttributeKey<ProviderSelection> ATTRIBUTE_SELECTION =
			ConflictAttributeKey.of("selection", ProviderSelection.class);
	public static final ConflictAttributeKey<String> ATTRIBUTE_INCOMING_PRIMARY_PROVIDER_ID =
			ConflictAttributeKey.string("incomingPrimaryProviderId");

	public static final ConflictAttributeKey<UUID> PARTICIPANT_ATTRIBUTE_ACCOUNT_UNIQUE_ID =
			ConflictAttributeKey.of("accountUniqueId", UUID.class);
	public static final ConflictAttributeKey<String> PARTICIPANT_ATTRIBUTE_USERNAME =
			ConflictAttributeKey.string("username");
	public static final ConflictAttributeKey<String> PARTICIPANT_ATTRIBUTE_PROVIDER_ID =
			ConflictAttributeKey.string("providerId");
	public static final ConflictAttributeKey<String> PARTICIPANT_ATTRIBUTE_PROVIDER_SUBJECT =
			ConflictAttributeKey.string("providerSubject");
	public static final ConflictAttributeKey<String> PARTICIPANT_ATTRIBUTE_PROVIDER_USERNAME =
			ConflictAttributeKey.string("providerUsername");

	public static final String EFFECT_CLOSE_EXISTING = "closeExisting";
	public static final String EFFECT_INCOMING_EFFECTIVE_USERNAME = "incomingEffectiveUsername";
	public static final String EFFECT_EXISTING_EFFECTIVE_USERNAME = "existingEffectiveUsername";
}

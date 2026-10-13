package me.whereareiam.identica.common.identity.username;

import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.identica.common.conflict.ConflictOutcomeSettings;
import me.whereareiam.identica.common.identity.username.outcome.RenameHolderOutcome;
import me.whereareiam.identica.model.conflict.ConflictRuleOutcome;
import me.whereareiam.identica.model.identity.username.UsernameConflictDecision;
import me.whereareiam.identica.model.identity.username.UsernameHolderRename;
import me.whereareiam.identica.type.conflict.ConflictSide;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.UsernameChangedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.config.UsernameConflicts;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.conflict.ConflictParty;
import me.whereareiam.identica.model.conflict.ConflictSituation;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.identity.username.UsernameConflict;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.model.identity.username.UsernameRename;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import me.whereareiam.identica.type.identity.username.UsernameHolderOnline;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Decides usernames under the username conflict rules.
 *
 * <p>An account wants the name its provider gives it. It keeps its name when an administrator set it, when the
 * provider is not the one its name follows, or when the name is its own already. A name nobody else holds is taken.
 * A held name brings a conflict up only when the account wants it for the first time or its provider renamed it;
 * otherwise the account waits under its renamed name until the name is free.</p>
 *
 * <p>While another player on this proxy still plays under the account's name, such as a holder renamed while
 * online, the player gets a temporary name for the session.</p>
 *
 * <p>A conflict between two accounts of the same provider that owns its names is settled without a rule: the
 * provider vouches for the arriving player now, so the holder's name is stale and the holder gets a temporary one.
 * An administrator's name is never changed, so a rule that would rename such a holder renames the arriving account
 * instead.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultUsernameAssigner implements UsernameAssigner {
	private static final int ATTEMPTS = 3;
	private static final RenameHolderOutcome RENAME_HOLDER = new RenameHolderOutcome();

	private final AccountUsernamePersistenceService usernames;
	private final ProviderLinkPersistenceService links;
	private final ProviderManager providers;
	private final ConflictService conflicts;
	private final UsernameConflictsProvider document;
	private final SessionService sessions;
	private final IdentityService identities;
	private final EventManager events;
	private final UsernameRenamer renamer;
	private final Provider<UsernameMessages> messages;

	@Override
	public @NotNull UsernameAssignment preview(@NotNull UsernameArrival arrival) {
		return playable(plan(arrival), arrival).assignment();
	}

	@Override
	public @NotNull UsernameAssignment assign(@NotNull UsernameArrival arrival) {
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Plan plan = playable(plan(arrival), arrival);
			if (plan.assignment().isRefused() || write(plan)) return plan.assignment();
		}

		Logger.warn("Account %s could not settle the username %s: other accounts kept taking the names it was given",
				arrival.getAccountUniqueId(), arrival.getUsername());
		return refuse(arrival.getUsername());
	}

	private @NotNull Plan plan(@NotNull UsernameArrival arrival) {
		UUID accountUniqueId = arrival.getAccountUniqueId();
		AccountUsername stored = accountUniqueId == null ? null : usernames.find(accountUniqueId).orElse(null);
		if (stored != null && stored.getSource() == AccountUsernameSource.MANUAL) return keep(stored, arrival);
		if (!arrival.isFirstJoin() && !followsProvider(arrival)) return keep(stored, arrival);

		String wanted = arrival.getUsername();
		if (stored != null && stored.getUsername().equals(wanted)) return keep(stored, arrival);

		AccountUsername holder = usernames.findHolder(wanted)
				.filter(found -> !found.getUniqueId().equals(accountUniqueId))
				.orElse(null);
		if (holder == null) return take(arrival, stored);

		UsernameConflictTrigger trigger = trigger(arrival);
		if (trigger == null) return keep(stored, arrival);

		return settle(arrival, stored, holder, trigger);
	}

	/**
	 * Gives the player a temporary name for this session while another player on this proxy still plays under the
	 * account's name: a holder that was renamed while online keeps the old name until leaving, and a holder being
	 * disconnected is still connected while the proxy names the player.
	 */
	private @NotNull Plan playable(@NotNull Plan plan, @NotNull UsernameArrival arrival) {
		UsernameAssignment assignment = plan.assignment();
		if (assignment.isRefused() || !isPlayedByAnother(assignment.getSessionUsername(), arrival.getAccountUniqueId()))
			return plan;

		String temporary = renamer.rename(
				Objects.requireNonNull(assignment.getUsername()),
				arrival.getProviderId(),
				settings(RENAME_HOLDER, arrival.getProviderId(), null),
				arrival.getAccountUniqueId(),
				Set.of(Objects.requireNonNull(assignment.getSessionUsername()))
		);
		if (temporary == null) return refusal(arrival.getUsername());

		UsernameAssignment playable = UsernameAssignment.of(assignment.getUsername(), temporary, Objects.requireNonNull(assignment.getSource()));
		return new Plan(playable, plan.holderChange(), plan.arrivingChange(), plan.kicked());
	}

	private boolean isPlayedByAnother(@Nullable String username, @Nullable UUID accountUniqueId) {
		if (username == null) return false;

		return identities.find(username)
				.map(Identity::getAccountUniqueId)
				.filter(holder -> !holder.equals(accountUniqueId))
				.isPresent();
	}

	private @Nullable UsernameConflictTrigger trigger(@NotNull UsernameArrival arrival) {
		if (arrival.isFirstJoin()) return UsernameConflictTrigger.FIRST_JOIN;

		String known = arrival.getKnownUsername();
		if (known != null && !known.equals(arrival.getUsername())) return UsernameConflictTrigger.PROVIDER_RENAME;

		return null;
	}

	private @NotNull Plan settle(
			@NotNull UsernameArrival arrival,
			@Nullable AccountUsername stored,
			@NotNull AccountUsername holder,
			@NotNull UsernameConflictTrigger trigger
	) {
		ConflictParty arriving = party(arrival.getAccountUniqueId(), arrival.getProviderId());
		ConflictParty holding = party(holder.getUniqueId(), primaryProvider(holder.getUniqueId()));
		boolean holderOnline = isOnline(holder.getUniqueId());

		if (isStale(arriving, holding)) {
			Logger.debug("Username %s of account %s is stale: provider %s gives it to account %s now",
					holder.getUsername(), holder.getUniqueId(), arrival.getProviderId(), arrival.getAccountUniqueId());
			UsernameHolderRename rename = settings(RENAME_HOLDER, holding.getProviderId(), null);
			return renameHolder(arrival, stored, holder, holding, UsernameConflictDecision.renameHolder(rename), holderOnline);
		}

		ConflictSituation<UsernameConflictTrigger> situation = ConflictSituation.<UsernameConflictTrigger>builder()
				.arriving(arriving)
				.holder(holding)
				.trigger(trigger)
				.arrivedVia(arrival.getArrivedVia())
				.build();
		UsernameConflictRule rule = conflicts.match(document.get().getRules(), situation).orElse(null);
		if (rule == null) {
			Logger.debug("No username conflict rule applies to %s; account %s is refused", situation, arrival.getAccountUniqueId());
			return refusal(arrival.getUsername());
		}

		UsernameConflictDecision decision = decide(UsernameConflict.builder()
				.situation(situation)
				.rule(rule)
				.username(arrival.getUsername())
				.holderUsername(holder.getUsername())
				.holderOnline(holderOnline)
				.build());
		Logger.debug("Username conflict over %s between account %s and holder %s settled by %s: %s",
				arrival.getUsername(), arrival.getAccountUniqueId(), holder.getUniqueId(), rule, decision);

		return switch (decision.getAction()) {
			case REFUSE -> refusal(arrival.getUsername());
			case RENAME_ARRIVING -> renameArriving(arrival, stored, Objects.requireNonNull(decision.getRename()));
			case RENAME_HOLDER -> renameHolder(arrival, stored, holder, holding, decision, holderOnline);
		};
	}

	private @NotNull UsernameConflictDecision decide(@NotNull UsernameConflict conflict) {
		ConflictRuleOutcome named = conflict.getRule().getOutcome();
		ConflictOutcome<UsernameConflict, UsernameConflictDecision, ?> outcome = conflicts.findOutcome(UsernameConflict.KIND, named.getId()).orElse(null);
		if (outcome == null) {
			Logger.debug("Username conflict rule names the outcome %s, which is not registered; the arriving player is refused", named.getId());
			return UsernameConflictDecision.refuse();
		}

		ConflictParty acted = outcome.actsOn() == ConflictSide.HOLDER ? conflict.getSituation().getHolder() : conflict.getSituation().getArriving();
		return decide(outcome, conflict, acted.getProviderId(), named.getWith());
	}

	private <S> @NotNull UsernameConflictDecision decide(
			@NotNull ConflictOutcome<UsernameConflict, UsernameConflictDecision, S> outcome,
			@NotNull UsernameConflict conflict,
			@Nullable String providerId,
			@Nullable ObjectNode with
	) {
		return outcome.decide(conflict, settings(outcome, providerId, with));
	}

	/**
	 * Resolves an outcome's settings: its defaults, changed by {@code outcomes.<id>}, then by
	 * {@code providers.<provider>.<id>} for the provider of the account it acts on, then by the rule.
	 */
	private <S> @NotNull S settings(
			@NotNull ConflictOutcome<?, ?, S> outcome,
			@Nullable String providerId,
			@Nullable ObjectNode with
	) {
		UsernameConflicts conflictsDocument = document.get();
		ObjectNode forProvider = providerId == null ? null : providerSettings(conflictsDocument, providerId, outcome.id());

		return ConflictOutcomeSettings.resolve(outcome, document.mapper(), Arrays.asList(
				conflictsDocument.getOutcomes().get(outcome.id()),
				forProvider,
				with
		));
	}

	private static @Nullable ObjectNode providerSettings(@NotNull UsernameConflicts document, @NotNull String providerId, @NotNull String outcomeId) {
		return document.getProviders().entrySet().stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase(providerId))
				.map(entry -> entry.getValue().get(outcomeId))
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private @NotNull Plan renameArriving(
			@NotNull UsernameArrival arrival,
			@Nullable AccountUsername stored,
			@NotNull UsernameRename rename
	) {
		String renamed = renamer.rename(arrival.getUsername(), arrival.getProviderId(), rename, arrival.getAccountUniqueId(), Set.of());
		if (renamed == null) return refusal(arrival.getUsername());
		if (stored != null && stored.getUsername().equals(renamed)) return keep(stored, arrival);

		UsernameAssignment assignment = UsernameAssignment.of(renamed, renamed, AccountUsernameSource.SYSTEM);
		return new Plan(assignment, null, change(stored, renamed, AccountUsernameSource.SYSTEM, null), null);
	}

	private @NotNull Plan renameHolder(
			@NotNull UsernameArrival arrival,
			@Nullable AccountUsername stored,
			@NotNull AccountUsername holder,
			@NotNull ConflictParty holding,
			@NotNull UsernameConflictDecision decision,
			boolean holderOnline
	) {
		UsernameRename rename = Objects.requireNonNull(decision.getRename());
		if (holder.getSource() == AccountUsernameSource.MANUAL) {
			Logger.debug("Holder %s of the username %s was named by an administrator; account %s is renamed instead",
					holder.getUniqueId(), holder.getUsername(), arrival.getAccountUniqueId());
			return renameArriving(arrival, stored, rename);
		}

		String holderName = renamer.rename(holder.getUsername(), holding.getProviderId(), rename, holder.getUniqueId(), Set.of());
		if (holderName == null) return refusal(arrival.getUsername());

		String wanted = arrival.getUsername();
		AccountUsernameHistoryEntry holderChange = AccountUsernameHistoryEntry.builder()
				.uniqueId(holder.getUniqueId())
				.oldUsername(holder.getUsername())
				.newUsername(holderName)
				.source(AccountUsernameSource.SYSTEM)
				.changedAt(System.currentTimeMillis())
				.build();
		AccountUsernameHistoryEntry arrivingChange = change(stored, wanted, AccountUsernameSource.PROVIDER, arrival.getProviderId());
		if (!holderOnline) {
			UsernameAssignment assignment = UsernameAssignment.of(wanted, wanted, AccountUsernameSource.PROVIDER);
			return new Plan(assignment, holderChange, arrivingChange, null);
		}

		if (decision.getHolderOnline() == UsernameHolderOnline.KICK_HOLDER) {
			UsernameAssignment assignment = UsernameAssignment.of(wanted, wanted, AccountUsernameSource.PROVIDER);
			return new Plan(assignment, holderChange, arrivingChange, holderChange);
		}

		String temporary = renamer.rename(wanted, arrival.getProviderId(), rename, arrival.getAccountUniqueId(), Set.of(holderName));
		if (temporary == null) return refusal(wanted);

		UsernameAssignment assignment = UsernameAssignment.of(wanted, temporary, AccountUsernameSource.PROVIDER);
		return new Plan(assignment, holderChange, arrivingChange, null);
	}

	private @NotNull Plan take(@NotNull UsernameArrival arrival, @Nullable AccountUsername stored) {
		String wanted = arrival.getUsername();
		UsernameAssignment assignment = UsernameAssignment.of(wanted, wanted, AccountUsernameSource.PROVIDER);

		return new Plan(assignment, null, change(stored, wanted, AccountUsernameSource.PROVIDER, arrival.getProviderId()), null);
	}

	private @NotNull Plan keep(@Nullable AccountUsername stored, @NotNull UsernameArrival arrival) {
		if (stored == null) {
			String wanted = arrival.getUsername();
			return new Plan(UsernameAssignment.of(wanted, wanted, AccountUsernameSource.PROVIDER), null, null, null);
		}

		UsernameAssignment assignment = UsernameAssignment.of(stored.getUsername(), stored.getUsername(), stored.getSource());
		return new Plan(assignment, null, null, null);
	}

	private @NotNull Plan refusal(@NotNull String username) {
		return new Plan(refuse(username), null, null, null);
	}

	private @NotNull UsernameAssignment refuse(@NotNull String username) {
		String lines = String.join("\n", messages.get().getPipeline().getPolicy().getTaken());

		return UsernameAssignment.refuse(Serializer.render(lines, Map.of("username", username)));
	}

	private static @Nullable AccountUsernameHistoryEntry change(
			@Nullable AccountUsername stored,
			@NotNull String username,
			@NotNull AccountUsernameSource source,
			@Nullable String providerId
	) {
		if (stored == null) return null;

		return AccountUsernameHistoryEntry.builder()
				.uniqueId(stored.getUniqueId())
				.providerId(providerId)
				.oldUsername(stored.getUsername())
				.newUsername(username)
				.source(source)
				.changedAt(System.currentTimeMillis())
				.build();
	}

	/**
	 * Writes a plan: the holder's new name first, which frees the wanted one, then the arriving account's. A name
	 * another account took in the meantime fails the plan, and the caller plans again.
	 */
	private boolean write(@NotNull Plan plan) {
		if (plan.holderChange() != null && !rename(plan.holderChange())) return false;
		if (plan.arrivingChange() != null && !rename(plan.arrivingChange())) return false;
		if (plan.kicked() != null) disconnect(plan.kicked());

		return true;
	}

	private boolean rename(@NotNull AccountUsernameHistoryEntry change) {
		UsernameWriteResult result = usernames.rename(change);
		if (!result.isWritten()) {
			Logger.debug("Account %s could not be renamed to %s: account %s holds it",
					change.getUniqueId(), change.getNewUsername(), result.getHolderUniqueId());
			return false;
		}

		events.call(new UsernameChangedEvent(
				change.getUniqueId(),
				change.getOldUsername(),
				change.getNewUsername(),
				change.getProviderId()
		));
		return true;
	}

	private void disconnect(@NotNull AccountUsernameHistoryEntry change) {
		String lines = String.join("\n", messages.get().getPipeline().getPolicy().getHolderReplaced());
		sessions.close(SessionCloseRequest.builder()
				.uniqueId(change.getUniqueId())
				.disconnect(true)
				.disconnectMessage(Serializer.render(lines, Map.of(
						"username", change.getOldUsername(),
						"renamed", change.getNewUsername()
				)))
				.build()).join();
	}

	private boolean followsProvider(@NotNull UsernameArrival arrival) {
		if (arrival.getAccountUniqueId() == null) return false;

		return links.findByUniqueIdAndProviderId(arrival.getAccountUniqueId(), arrival.getProviderId())
				.map(AccountProviderLink::isPrimaryLink)
				.orElse(false);
	}

	private boolean isOnline(@NotNull UUID uniqueId) {
		return !sessions.findAllByUniqueId(uniqueId).join().isEmpty();
	}

	private @Nullable String primaryProvider(@NotNull UUID uniqueId) {
		List<AccountProviderLink> accountLinks = links.findByUniqueId(uniqueId);
		return accountLinks.stream()
				.filter(AccountProviderLink::isPrimaryLink)
				.findFirst()
				.or(() -> accountLinks.stream().findFirst())
				.map(AccountProviderLink::getProviderId)
				.orElse(null);
	}

	private @NotNull ConflictParty party(@Nullable UUID uniqueId, @Nullable String providerId) {
		return ConflictParty.builder()
				.accountUniqueId(uniqueId)
				.providerId(providerId)
				.guarantees(guarantees(providerId))
				.build();
	}

	private @NotNull Set<ProviderGuarantee> guarantees(@Nullable String providerId) {
		if (providerId == null) return Set.of();

		return providers.getProviders().stream()
				.map(InternalProvider::getDescriptor)
				.filter(Objects::nonNull)
				.filter(descriptor -> descriptor.getId().equalsIgnoreCase(providerId))
				.findFirst()
				.map(ProviderDescriptor::getGuarantees)
				.orElse(Set.of());
	}

	private static boolean isStale(@NotNull ConflictParty arriving, @NotNull ConflictParty holding) {
		return arriving.getProviderId() != null
				&& arriving.getProviderId().equalsIgnoreCase(holding.getProviderId())
				&& arriving.getGuarantees().contains(ProviderGuarantee.OWNED_USERNAME);
	}

	/**
	 * What a decision writes: the holder's rename, the arriving account's rename when it is stored, and the holder
	 * to disconnect.
	 */
	private record Plan(
			@NotNull UsernameAssignment assignment,
			@Nullable AccountUsernameHistoryEntry holderChange,
			@Nullable AccountUsernameHistoryEntry arrivingChange,
			@Nullable AccountUsernameHistoryEntry kicked
	) {
	}
}

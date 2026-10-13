package me.whereareiam.identica.common.config.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.defaults.UsernameConflictsDefaults;
import me.whereareiam.identica.common.conflict.ConflictDocument;
import me.whereareiam.identica.common.conflict.ConflictRuleOverlaps;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.UsernameConflicts;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.identity.username.UsernameConflict;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Loads the username conflict rules from {@code identity/username/conflicts}. It warns about equally specific rules
 * that can apply to the same conflict but settle it differently, reports rules naming an outcome nobody registered
 * once the outcomes are known, and writes the defaults of every registered outcome the document does not hold.
 */
@Singleton
public class UsernameConflictsProvider extends ConfigProvider<UsernameConflicts> implements ConflictDocument {
	private final Provider<ConflictService> conflicts;
	private volatile boolean checkingOutcomes;

	@Inject
	public UsernameConflictsProvider(
			@Named("usernamePath") Path featurePath,
			Registry<Reloadable> reloadables,
			Provider<ConflictService> conflicts
	) {
		super(featurePath, "conflicts", UsernameConflicts.class, reloadables);
		this.conflicts = conflicts;
	}

	@Override
	public @NotNull ConflictKind<?, ?> kind() {
		return UsernameConflict.KIND;
	}

	@Override
	public synchronized void include(@NotNull ConflictOutcome<?, ?, ?> outcome) {
		UsernameConflicts document = get();
		if (document.getOutcomes().containsKey(outcome.id())) return;

		document.getOutcomes().put(outcome.id(), configura().mapper().<ObjectNode>valueToTree(outcome.defaults()));
		configura().save(getPath(), document);
	}

	/**
	 * Reports every rule whose outcome nobody registered, and keeps doing so on every reload. Such a rule refuses
	 * the arriving player instead.
	 */
	public void checkOutcomes() {
		checkingOutcomes = true;
		List<UsernameConflictRule> rules = get().getRules();
		for (int index = 0; index < rules.size(); index++) {
			String id = rules.get(index).getOutcome().getId();
			if (conflicts.get().findOutcome(UsernameConflict.KIND, id).isPresent()) continue;

			Logger.severe("Username conflict rule %d names the outcome \"%s\", which is not registered; the rule refuses the arriving player instead",
					index + 1, id);
		}
	}

	/**
	 * Returns the mapper the document is read with, which outcome settings are resolved with.
	 *
	 * @return the mapper
	 */
	public @NotNull ObjectMapper mapper() {
		return configura().mapper();
	}

	@Override
	protected @NotNull UsernameConflicts load() {
		UsernameConflicts document = super.load();
		warnAboutOverlaps(document.getRules());

		return document;
	}

	@Override
	public void reload() {
		super.reload();
		if (checkingOutcomes) checkOutcomes();
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(UsernameConflictsDefaults.class);
	}

	private void warnAboutOverlaps(@NotNull List<UsernameConflictRule> rules) {
		for (ConflictRuleOverlaps.Overlap overlap : ConflictRuleOverlaps.find(rules, (first, second) -> first.getOutcome().equals(second.getOutcome()))) {
			Logger.warn(
					"Username conflict rules %d and %d are equally specific and can apply to the same conflict, but settle it differently; rule %d applies there",
					overlap.first() + 1,
					overlap.second() + 1,
					overlap.first() + 1
			);
		}
	}
}

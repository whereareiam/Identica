package me.whereareiam.identica.common.conflict;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.conflict.ConflictRule;
import me.whereareiam.identica.model.conflict.ConflictSituation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DefaultConflictService implements ConflictService {
	private final Provider<Set<ConflictDocument>> documents;
	private final Map<OutcomeKey, ConflictOutcome<?, ?, ?>> outcomes = new ConcurrentHashMap<>();

	@Inject
	public DefaultConflictService(Provider<Set<ConflictDocument>> documents) {
		this.documents = documents;
	}

	@Override
	public void register(@NotNull ConflictOutcome<?, ?, ?> outcome) {
		outcomes.put(OutcomeKey.of(outcome.kind(), outcome.id()), outcome);
		for (ConflictDocument document : documents.get())
			if (document.kind().equals(outcome.kind())) document.include(outcome);
	}

	@Override
	public void unregister(@NotNull ConflictOutcome<?, ?, ?> outcome) {
		outcomes.remove(OutcomeKey.of(outcome.kind(), outcome.id()), outcome);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <C, D> @NotNull Optional<ConflictOutcome<C, D, ?>> findOutcome(@NotNull ConflictKind<C, D> kind, @NotNull String id) {
		// Registration keys an outcome by its own kind, so the outcome under a kind's key settles that kind.
		return Optional.ofNullable((ConflictOutcome<C, D, ?>) outcomes.get(OutcomeKey.of(kind, id)));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <C, D> @NotNull List<ConflictOutcome<C, D, ?>> outcomes(@NotNull ConflictKind<C, D> kind) {
		return outcomes.entrySet().stream()
				.filter(entry -> entry.getKey().kind().equals(kind.getKey()))
				.<ConflictOutcome<C, D, ?>>map(entry -> (ConflictOutcome<C, D, ?>) entry.getValue())
				.toList();
	}

	@Override
	public <T extends Enum<T>, R extends ConflictRule<T>> @NotNull Optional<R> match(
			@NotNull List<R> rules,
			@NotNull ConflictSituation<T> situation
	) {
		R matched = null;
		ConflictRuleMatcher.Specificity best = null;
		for (R rule : rules) {
			if (!ConflictRuleMatcher.applies(rule, situation)) continue;

			ConflictRuleMatcher.Specificity specificity = ConflictRuleMatcher.specificity(rule);
			if (best != null && specificity.compareTo(best) <= 0) continue;

			matched = rule;
			best = specificity;
		}

		return Optional.ofNullable(matched);
	}

	private record OutcomeKey(String kind, String id) {
		private static OutcomeKey of(ConflictKind<?, ?> kind, String id) {
			return new OutcomeKey(kind.getKey(), id.trim().toLowerCase(Locale.ROOT));
		}
	}
}

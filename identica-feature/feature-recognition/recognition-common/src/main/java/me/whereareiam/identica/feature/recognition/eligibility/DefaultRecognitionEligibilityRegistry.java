package me.whereareiam.identica.feature.recognition.eligibility;

import com.google.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class DefaultRecognitionEligibilityRegistry implements RecognitionEligibilityRegistry {
	private final List<RecognitionEligibilityRule> rules = new CopyOnWriteArrayList<>();

	@Override
	public void register(@NotNull RecognitionEligibilityRule rule) {
		String id = rule.id();
		if (id.isBlank()) return;

		String normalized = normalize(id);
		rules.removeIf(existing -> existing != null && normalize(existing.id()).equals(normalized));
		rules.add(rule);
	}

	@Override
	public boolean unregister(@NotNull String ruleId) {
		if (ruleId.isBlank()) return false;

		String normalized = normalize(ruleId);
		return rules.removeIf(existing -> existing != null && normalize(existing.id()).equals(normalized));
	}

	@Override
	public @NotNull List<RecognitionEligibilityRule> resolve() {
		return List.copyOf(rules);
	}

	private @NotNull String normalize(@NotNull String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}
}

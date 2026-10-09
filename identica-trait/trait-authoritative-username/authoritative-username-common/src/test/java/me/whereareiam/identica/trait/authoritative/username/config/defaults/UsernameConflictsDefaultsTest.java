package me.whereareiam.identica.trait.authoritative.username.config.defaults;

import me.whereareiam.identica.model.config.provider.Conflicts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Conflicts Defaults")
class UsernameConflictsDefaultsTest {
	@DisplayName("Username conflict defaults are generated in the conflicts config")
	@Test
	void usernameConflictDefaultsAreGenerated() {
		Conflicts conflicts = new UsernameConflictsDefaults().supply(new Conflicts());

		Conflicts.ConflictRules rules = conflicts.getRules().get("username");
		assertNotNull(rules);
		assertEquals(1, rules.getDefaultRule().getResolvers().size());
		assertEquals("format_display", rules.getDefaultRule().getResolvers().getFirst().getId());
		assertEquals(1, rules.getCases().size());
		assertEquals("premium", rules.getCases().getFirst().getWhen().path("providers").get(0).asText());
		assertEquals("credential", rules.getCases().getFirst().getWhen().path("providers").get(1).asText());
	}
}

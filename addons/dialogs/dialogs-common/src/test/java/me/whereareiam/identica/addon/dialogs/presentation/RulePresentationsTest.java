package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.DialogPart;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.ChatPart;
import me.whereareiam.identica.model.message.Presentation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RulePresentationsTest {
	private final MessageService messages = mock(MessageService.class);
	private final MessageService.Registration registration = mock(MessageService.Registration.class);
	private final RulePresentations presentations = new RulePresentations(messages);

	/**
	 * A rule that names a message is presented by the message's ID, which then also decides its prompt; a rule naming
	 * only a prompt is presented by the prompt, once however many rules name it; a rule naming neither is left out.
	 */
	@Test
	void presentsThePromptsAndMessagesTheRulesNameWithADialogInsteadOfChat() {
		when(messages.presentPrompt(anyString(), any())).thenReturn(registration);
		when(messages.present(anyString(), any())).thenReturn(registration);

		presentations.apply(List.of(
				DialogRule.builder().when("prompt", "Credential:Password").when("host", "event.example.net").dialog("event").build(),
				DialogRule.builder().when("prompt", "credential:password").dialog("login").build(),
				DialogRule.builder().when("message", "credential:password-invalid").when("prompt", "credential:password").dialog("wrong").build(),
				DialogRule.builder().when("host", "event.example.net").dialog("event").build()
		));

		ArgumentCaptor<Presentation> presentation = ArgumentCaptor.forClass(Presentation.class);
		verify(messages).presentPrompt(eq("credential:password"), presentation.capture());
		verify(messages).present(eq("credential:password-invalid"), any());
		verifyNoMoreInteractions(messages);
		assertEquals(List.of(DialogPart.byRules()), presentation.getValue().getParts());
		assertEquals(Set.of(ChatPart.TYPE), presentation.getValue().getRemoved());
	}

	@Test
	void replacesThePresentationsWhenTheRulesAreAppliedAgain() {
		when(messages.presentPrompt(anyString(), any())).thenReturn(registration);
		List<DialogRule> rules = List.of(DialogRule.builder().when("prompt", "credential:password").dialog("login").build());

		presentations.apply(rules);
		presentations.apply(rules);
		presentations.remove();

		verify(messages, times(2)).presentPrompt(eq("credential:password"), any());
		verify(registration, times(2)).remove();
	}
}

package me.whereareiam.identica.pipeline.state;

import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.pipeline.state.exception.UnreadablePipelineStateException;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pipeline State")
class PipelineStateTest {
	@DisplayName("Returns the value a key holds, so a change to it needs no further write")
	@Test
	void returnsTheHeldValue() {
		PipelineState state = PipelineState.initial();
		PrepareContextItem context = state.getOrCreate(PrepareContextItem.KEY, PrepareContextItem::new);

		context.setChoice(JourneyChoice.PROMPTED);

		assertSame(context, state.get(PrepareContextItem.KEY).orElseThrow());
		assertEquals(JourneyChoice.PROMPTED, state.get(PrepareContextItem.KEY).orElseThrow().getChoice());
	}

	@DisplayName("Never saves the values of run keys")
	@Test
	void storeLeavesRunValuesOut() {
		PipelineState state = PipelineState.initial();
		state.put(PrepareContextItem.KEY, new PrepareContextItem());
		state.put(JourneyStateItem.KEY, new JourneyStateItem("provider", 1));

		StoredPipelineState stored = state.store();

		assertEquals(1, stored.getValues().size());
		assertTrue(stored.getValues().containsKey(JourneyStateItem.KEY.id()));
		assertTrue(state.has(PrepareContextItem.KEY), "storing changes the state it stores");
	}

	@DisplayName("Restores persisted values and where the pipeline stands")
	@Test
	void restoresPersistedValues() {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.AUTHENTICATION);
		state.put(JourneyStateItem.KEY, new JourneyStateItem("provider", 1));

		PipelineState restored = PipelineState.restore(state.store());
		JourneyStateItem position = restored.get(JourneyStateItem.KEY).orElseThrow();

		assertEquals(PipelineType.AUTHENTICATION, restored.getPipelineType());
		assertEquals("provider", position.getStageId());
		assertEquals(1, position.getStepIndex());
	}

	@DisplayName("Fails a read of a saved value that cannot be read back, naming its key")
	@Test
	void unreadableValueFailsTheRead() {
		StoredPipelineState stored = PipelineState.initial().store();
		stored.setValues(Map.of(JourneyStateItem.KEY.id(), new StoredPipelineState.StoredValue("{\"stepIndex\": \"first\"}", 0L)));
		PipelineState restored = PipelineState.restore(stored);

		UnreadablePipelineStateException failure = assertThrows(
				UnreadablePipelineStateException.class,
				() -> restored.get(JourneyStateItem.KEY)
		);

		assertEquals(JourneyStateItem.KEY.id(), failure.getKeyId());
	}

	@DisplayName("Keeps a saved value it has not read when it is saved again")
	@Test
	void storeKeepsValuesItHasNotRead() {
		PipelineState state = PipelineState.initial();
		state.put(JourneyStateItem.KEY, new JourneyStateItem("provider", 1));

		PipelineState restored = PipelineState.restore(PipelineState.restore(state.store()).store());

		assertEquals("provider", restored.get(JourneyStateItem.KEY).orElseThrow().getStageId());
	}

	@DisplayName("Drops a value once its time to live has passed")
	@Test
	void dropsExpiredValues() {
		StoredPipelineState stored = PipelineState.initial().store();
		stored.setValues(Map.of(JourneyStateItem.KEY.id(), new StoredPipelineState.StoredValue("{}", 1L)));
		PipelineState restored = PipelineState.restore(stored);

		assertFalse(restored.has(JourneyStateItem.KEY));
		assertTrue(restored.get(JourneyStateItem.KEY).isEmpty());
		assertTrue(restored.isEmpty());
	}

	@DisplayName("Removes the values of run keys and keeps the others")
	@Test
	void removesRunValues() {
		PipelineState state = PipelineState.initial();
		state.put(PrepareContextItem.KEY, new PrepareContextItem());
		state.put(JourneyStateItem.KEY, new JourneyStateItem());

		state.removeRunValues();

		assertFalse(state.has(PrepareContextItem.KEY));
		assertTrue(state.has(JourneyStateItem.KEY));
	}
}

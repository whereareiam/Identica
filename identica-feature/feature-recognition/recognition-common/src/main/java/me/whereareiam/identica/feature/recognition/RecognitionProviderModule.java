package me.whereareiam.identica.feature.recognition;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.feature.ProviderFeatureContribution;
import me.whereareiam.identica.feature.recognition.compatibility.restriction.join.RecognitionJoinSignalContribution;

public class RecognitionProviderModule extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), ProviderFeatureContribution.class)
				.addBinding()
				.to(RecognitionJoinSignalContribution.class);
	}
}

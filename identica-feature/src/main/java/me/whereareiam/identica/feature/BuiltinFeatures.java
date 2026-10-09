package me.whereareiam.identica.feature;

import me.whereareiam.identica.common.feature.FeatureRuntime;
import me.whereareiam.identica.feature.recognition.RecognitionFeature;
import me.whereareiam.identica.feature.restriction.RestrictionFeature;
import me.whereareiam.identica.feature.restriction.join.JoinRestrictionFeature;
import me.whereareiam.identica.feature.sentinel.SentinelFeatureBootstrap;
import me.whereareiam.identica.feature.verification.VerificationFeatureBootstrap;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/** Composes the feature implementations compiled into Identica. */
public final class BuiltinFeatures {
	public static @NotNull FeatureRuntime runtime(@NotNull Path dataPath) {
		return FeatureRuntime.prepare(dataPath, List.of(
				new RecognitionFeature(),
				new RestrictionFeature(),
				new JoinRestrictionFeature(),
				new SentinelFeatureBootstrap(),
				new VerificationFeatureBootstrap()
		));
	}
}

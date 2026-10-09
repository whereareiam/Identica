# Development overlays

The `normal`, `extended`, and `replication` scenarios contain proxy overlays for Velocity and BungeeCord. These scenarios start long-running servers for manual validation; inspect their logs when checking startup or player flows.

All feature implementations and identity behavior are compiled into Identica. Build with `./gradlew pluginJars`.

- `features/recognition/settings.yml` enables recognition and disables the untrusted-IP eligibility filter for local scenario traffic.
- `providers/providers.yml` configures both official providers under `features.restriction.join`, enabling join restriction and allowing the `linked` and `recognized` signals.

To exercise provider isolation, set `features.recognition.enabled: false` for one provider and true for the other in the providers document. Omitted provider fields inherit shared defaults. Recognition can operate independently of join restrictions.

Username synchronization follows Premium's `AUTHORITATIVE_USERNAME` trait. Its conflict policy and messages live under `identity/username/`; there is no username feature switch.

For Sentinel, configure shared `enabled`, `sentinels.resumeSpam`, and `replication.state` in `features/sentinel/settings.yml`, and `resumeSpam.denied` in its messages file. Both providers support per-provider sentinel enablement once a provider is known; connection checks before provider selection use shared policy. Credential additionally contributes its own brute-force rule, scoped to Credential attempts.

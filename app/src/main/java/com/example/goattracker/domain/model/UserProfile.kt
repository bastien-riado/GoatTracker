package com.example.goattracker.domain.model

/**
 * Display/input unit for weights. Storage is ALWAYS kilograms ([WorkoutSet.weight],
 * [UserProfile.bodyWeightKg]); conversion happens only at the UI boundary so the data layer and the
 * volume engine never deal with mixed units.
 */
enum class WeightUnit(val displayName: String, val suffix: String) {
    KG("Kilogrammes", "kg"),
    LBS("Livres", "lbs");

    fun fromKg(kg: Double): Double = if (this == KG) kg else kg * LBS_PER_KG

    fun toKg(value: Double): Double = if (this == KG) value else value / LBS_PER_KG

    companion object {
        const val LBS_PER_KG = 2.2046226218
    }
}

/** Where the current body weight value came from (shown in settings, decides sync overwrites). */
enum class BodyWeightSource {
    MANUAL,
    HEALTH_CONNECT
}

/**
 * One body-weight observation. The profile only keeps the CURRENT value; the history (fed by the
 * data layer on every weight change since the Room migration) is what weight curves are built on.
 */
data class BodyWeightEntry(
    val weightKg: Double,
    val measuredAt: Long,
    val source: BodyWeightSource,
)

/**
 * App-level user profile. The body weight is the single value used to compute volume for every
 * BODYWEIGHT_REPS exercise — past sessions included (deliberate product choice: history aligns with
 * the *current* weight rather than the weight at the time of the session).
 */
data class UserProfile(
    /** Body weight in kilograms; null until the user sets it (or Health Connect provides it). */
    val bodyWeightKg: Double? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** User opt-in: refresh the body weight from Health Connect on app start. */
    val healthConnectSyncEnabled: Boolean = false,
    val bodyWeightUpdatedAt: Long? = null,
    val bodyWeightSource: BodyWeightSource = BodyWeightSource.MANUAL,
)

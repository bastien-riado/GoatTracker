package com.example.goattracker.domain.model

/**
 * Canonical muscle groups used by the 3D body heatmap.
 *
 * [id] is the contract with the 3D asset: it MUST match the material name baked into
 * `assets/models/body_muscles.glb` (one named material per muscle). At runtime the renderer looks
 * up each glTF material by this id and tints its `baseColorFactor`. Do not rename an [id] without
 * re-exporting the model.
 *
 * [label] is the French display name shown in the legend / selection UI.
 */
enum class MuscleGroup(val id: String, val label: String) {
    CHEST("chest", "Pectoraux"),
    FRONT_DELTS("front_delts", "Épaules (avant)"),
    REAR_DELTS("rear_delts", "Épaules (arrière)"),
    BICEPS("biceps", "Biceps"),
    TRICEPS("triceps", "Triceps"),
    FOREARMS("forearms", "Avant-bras"),
    ABS("abs", "Abdominaux"),
    OBLIQUES("obliques", "Obliques"),
    LATS("lats", "Grand dorsal"),
    TRAPS("traps", "Trapèzes"),
    LOWER_BACK("lower_back", "Lombaires"),
    GLUTES("glutes", "Fessiers"),
    QUADS("quads", "Quadriceps"),
    HAMSTRINGS("hamstrings", "Ischio-jambiers"),
    CALVES("calves", "Mollets");

    companion object {
        /**
         * glTF material names present in the model that are NOT muscles: `head` (head/neck) and
         * `body` (hands, feet, shins, knees, groin, clavicles and any untracked area). The
         * renderer tints any material whose name has no [MuscleGroup] as neutral grey.
         */
        val NEUTRAL_MATERIAL_IDS: Set<String> = setOf("head", "body")

        fun fromId(id: String): MuscleGroup? = entries.firstOrNull { it.id == id }
    }
}

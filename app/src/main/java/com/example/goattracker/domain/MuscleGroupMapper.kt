package com.example.goattracker.domain

import com.example.goattracker.domain.model.MuscleGroup
import java.text.Normalizer

/**
 * Maps the free-form [com.example.goattracker.domain.model.Exercise.primaryMuscle] String (entered
 * by the user, in French, unvalidated) onto a canonical [MuscleGroup] for the 3D heatmap.
 *
 * Matching is accent- and case-insensitive. Unknown muscles map to `null` (the heatmap leaves them
 * neutral rather than failing) — this is the single place that absorbs the "free-form field" risk.
 */
object MuscleGroupMapper {

    // Normalized synonym -> group. Keys must already be lowercase and accent-free (see [normalize]).
    private val SYNONYMS: Map<String, MuscleGroup> = buildMap {
        fun add(group: MuscleGroup, vararg keys: String) = keys.forEach { put(normalize(it), group) }

        add(MuscleGroup.CHEST, "pectoraux", "pectoral", "pecs", "pec", "poitrine", "chest")
        add(MuscleGroup.REAR_DELTS, "epaules arriere", "epaule arriere", "arriere epaules",
            "deltoides posterieurs", "deltoide posterieur", "rear delts", "rear deltoid")
        add(MuscleGroup.FRONT_DELTS, "epaules avant", "epaule avant", "deltoides anterieurs",
            "deltoide anterieur", "front delts", "front deltoid",
            // Generic shoulder terms fall to the front head of the deltoid.
            "epaules", "epaule", "deltoides", "deltoide", "delts", "deltoid", "shoulders", "shoulder")
        add(MuscleGroup.BICEPS, "biceps", "biceps brachial", "curl")
        add(MuscleGroup.TRICEPS, "triceps", "triceps brachial")
        add(MuscleGroup.FOREARMS, "avant bras", "avantbras", "forearms", "forearm")
        add(MuscleGroup.ABS, "abdominaux", "abdos", "abdo", "abs", "abdominal",
            "sangle abdominale", "gainage", "core")
        add(MuscleGroup.OBLIQUES, "obliques", "oblique")
        add(MuscleGroup.LATS, "dos", "grand dorsal", "dorsaux", "dorsal", "lats", "latissimus",
            "back", "tirage")
        add(MuscleGroup.TRAPS, "trapezes", "trapeze", "traps", "trapezius")
        add(MuscleGroup.LOWER_BACK, "lombaires", "lombaire", "bas du dos", "erecteurs", "erecteur",
            "lower back", "lumbar")
        add(MuscleGroup.GLUTES, "fessiers", "fessier", "glutes", "gluteus", "gluteaux", "gluteal")
        add(MuscleGroup.QUADS, "quadriceps", "quadricep", "quads", "cuisses", "cuisse")
        add(MuscleGroup.HAMSTRINGS, "ischio jambiers", "ischio jambier", "ischiojambiers", "ischios",
            "ischio", "hamstrings", "hamstring")
        // "triceps sural" must out-rank the "triceps" substring; longest-key-first ordering handles it.
        add(MuscleGroup.CALVES, "mollets", "mollet", "calves", "calf", "jumeaux", "triceps sural")
    }

    // Longest keys first so multi-word synonyms win over their substrings in the contains() fallback.
    private val keysByLengthDesc: List<String> = SYNONYMS.keys.sortedByDescending { it.length }

    fun map(raw: String?): MuscleGroup? {
        if (raw.isNullOrBlank()) return null
        val n = normalize(raw)
        if (n.isEmpty()) return null
        SYNONYMS[n]?.let { return it }
        for (key in keysByLengthDesc) {
            if (n.contains(key)) return SYNONYMS.getValue(key)
        }
        return null
    }

    /** lowercase, strip diacritics, collapse non-alphanumerics to single spaces. */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

package com.example.goattracker

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data class CreateExercise(val exerciseId: String? = null) : NavKey
@Serializable data class LiveWorkout(val sessionId: String? = null) : NavKey
@Serializable data object Profile : NavKey
@Serializable data object SessionsList : NavKey
@Serializable data object BodyHeatmap : NavKey
@Serializable data class ExerciseDetail(val exerciseId: String) : NavKey
@Serializable data object Settings : NavKey
@Serializable data object PatchNotes : NavKey
@Serializable data object Templates : NavKey
@Serializable data class TemplateEditor(val templateId: String? = null) : NavKey
@Serializable data class SessionDetail(val sessionId: String) : NavKey
@Serializable data class SessionCelebration(val sessionId: String) : NavKey
@Serializable data object RecoverySettings : NavKey


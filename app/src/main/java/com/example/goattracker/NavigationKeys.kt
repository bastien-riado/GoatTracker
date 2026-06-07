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


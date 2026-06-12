package com.example.goattracker.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateListItem(
    val template: WorkoutTemplate,
    /** Resolved for display (archived exercises included — a template may reference them). */
    val exerciseNames: List<String>,
)

class TemplatesViewModel(
    private val repository: DataRepository,
    private val launcher: TemplateLauncher = TemplateLauncher(repository),
) : ViewModel() {

    val items: StateFlow<List<TemplateListItem>> = repository.templates
        .map { templates ->
            templates.map { template ->
                TemplateListItem(
                    template = template,
                    exerciseNames = template.entries.mapNotNull { entry ->
                        repository.getExercise(entry.exerciseId)?.name
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(templateId: String) {
        viewModelScope.launch { repository.deleteWorkoutTemplate(templateId) }
    }

    // Double-tap guard: two fast taps on "Lancer" must not race two draft writes.
    private var launching = false

    fun launch(template: WorkoutTemplate, onOpen: () -> Unit) {
        if (launching) return
        launching = true
        viewModelScope.launch {
            try {
                launcher.launch(template)
                onOpen() // false = a session is already live; opening it is the right outcome too
            } finally {
                launching = false
            }
        }
    }
}

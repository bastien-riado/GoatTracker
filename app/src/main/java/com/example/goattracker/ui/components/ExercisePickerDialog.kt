package com.example.goattracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.theme.Accent
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.BorderSoft
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.Muted
import com.example.goattracker.theme.Surface
import com.example.goattracker.theme.SurfaceElevated
import com.example.goattracker.ui.main.CategoryChip

/**
 * THE exercise picker of the app — search + category filter + optional "create new" shortcut.
 * Extracted from the live session screen so every "Ajouter un exercice" entry point (live session,
 * workout editor…) opens the exact same modal instead of each screen inventing its own list.
 */
@Composable
fun ExercisePickerDialog(
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    onCreateExercise: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false lets imePadding() react to the keyboard inside the
        // dialog window so the list/search rise above it; usePlatformDefaultWidth = false lets
        // us control the width ourselves.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .border(1.dp, Border, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ajouter un exercice",
                        color = Fg,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Fg)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                var searchQuery by remember { mutableStateOf("") }
                var categoryFilter by remember { mutableStateOf<ExerciseCategory?>(null) }

                AppTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Rechercher un exercice...",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Muted) },
                    capitalization = KeyboardCapitalization.None,
                    containerColor = SurfaceElevated
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryChip(
                        text = "Tous",
                        isSelected = categoryFilter == null,
                        onClick = { categoryFilter = null }
                    )
                    ExerciseCategory.values().forEach { category ->
                        CategoryChip(
                            text = category.displayName,
                            isSelected = categoryFilter == category,
                            onClick = { categoryFilter = category }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (onCreateExercise != null) {
                    OutlinedButton(
                        onClick = onCreateExercise,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(listOf(Accent, Accent))
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Créer un nouvel exercice", color = Accent, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                val filteredList = exercises.filter { exercise ->
                    val matchesQuery = exercise.name.contains(searchQuery, ignoreCase = true) ||
                            exercise.primaryMuscle.contains(searchQuery, ignoreCase = true)
                    val matchesCategory = categoryFilter == null || exercise.category == categoryFilter
                    matchesQuery && matchesCategory
                }

                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Aucun exercice trouvé", color = Muted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList) { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderSoft, RoundedCornerShape(8.dp))
                                    .clickable { onPick(exercise) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(exercise.name, color = Fg, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${exercise.category.displayName} • ${exercise.primaryMuscle}",
                                        color = Muted,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                }
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

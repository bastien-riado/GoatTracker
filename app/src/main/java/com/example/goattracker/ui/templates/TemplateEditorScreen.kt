package com.example.goattracker.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.theme.Bg
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.BorderSoft
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.Muted
import com.example.goattracker.theme.PremiumGradient
import com.example.goattracker.theme.Surface
import com.example.goattracker.theme.SurfaceElevated
import com.example.goattracker.ui.components.AppNumberField
import com.example.goattracker.ui.components.AppTextField
import com.example.goattracker.ui.create.FormLabel
import com.example.goattracker.ui.create.SelectableTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: String? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uniqueKey = remember(templateId) { templateId ?: java.util.UUID.randomUUID().toString() }
    val viewModel: TemplateEditorViewModel = viewModel(key = uniqueKey) {
        TemplateEditorViewModel(RoomDataRepository.getInstance(context), templateId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExercisePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.savedEvents.collect { onBackClick() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (templateId != null) "Modifier le workout" else "Créer un workout",
                        color = Fg,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.dp, Border, CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Fg)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = Fg)
            )
        },
        containerColor = Bg
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(text = "Nom du workout")
                AppTextField(
                    value = state.name,
                    onValueChange = { viewModel.updateName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "ex: Push, Pull, Leg…"
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(text = "Exercices (dans l'ordre de la séance)")

                if (state.rows.isEmpty()) {
                    Text(
                        text = "Ajoute les exercices de cette séance type — séries et objectifs seront pré-remplis au lancement.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }

                state.rows.forEachIndexed { index, row ->
                    EditorRowCard(
                        row = row,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.rows.lastIndex,
                        weightSuffix = state.weightUnit.suffix,
                        onMoveUp = { viewModel.moveRow(row.entryId, -1) },
                        onMoveDown = { viewModel.moveRow(row.entryId, +1) },
                        onRemove = { viewModel.removeRow(row.entryId) },
                        onSetsChange = { viewModel.updateSets(row.entryId, it) },
                        onRepsChange = { viewModel.updateReps(row.entryId, it) },
                        onWeightChange = { viewModel.updateWeight(row.entryId, it) },
                        onToggleAmrap = { viewModel.toggleAmrap(row.entryId) },
                    )
                }

                Button(
                    onClick = { showExercisePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, Border, RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Fg, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ajouter un exercice", color = Fg, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = { viewModel.save() },
                enabled = state.isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, if (state.isSaveEnabled) Border else Color.Transparent, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = SurfaceElevated
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (state.isSaveEnabled) Modifier.background(PremiumGradient) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (templateId != null) "Enregistrer les modifications" else "Enregistrer le workout",
                        color = if (state.isSaveEnabled) Fg else Muted,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Clearance for the session mini-player docked by the navigation root.
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    if (showExercisePicker) {
        ModalBottomSheet(
            onDismissRequest = { showExercisePicker = false },
            containerColor = Surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "AJOUTER UN EXERCICE",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LazyColumn {
                    items(state.availableExercises, key = { it.id }) { exercise ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addExercise(exercise)
                                    showExercisePicker = false
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exercise.name, color = Fg, fontWeight = FontWeight.SemiBold)
                                Text(exercise.primaryMuscle, color = Muted, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.Add, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorRowCard(
    row: EditorRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    weightSuffix: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetsChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onToggleAmrap: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.exercise.name,
                    color = Fg,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Monter",
                        tint = if (canMoveUp) Fg else Muted.copy(alpha = 0.4f)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Descendre",
                        tint = if (canMoveDown) Fg else Muted.copy(alpha = 0.4f)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Retirer", tint = Muted)
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormLabel(text = "Séries")
                    AppNumberField(
                        value = row.setsText,
                        onValueChange = onSetsChange,
                        modifier = Modifier.width(64.dp)
                    )
                }
                if (row.showsRepTargets) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FormLabel(text = "Reps")
                        if (row.isAmrap) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(40.dp)
                                    .background(Surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSoft, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("MAX", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            AppNumberField(
                                value = row.repsText,
                                onValueChange = onRepsChange,
                                modifier = Modifier.width(64.dp)
                            )
                        }
                    }
                }
                if (row.showsWeightTarget) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FormLabel(text = "Poids ($weightSuffix)")
                        AppNumberField(
                            value = row.weightText,
                            onValueChange = onWeightChange,
                            modifier = Modifier.width(80.dp),
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                }
                if (row.showsRepTargets) {
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        SelectableTag(
                            text = "À l'échec",
                            isSelected = row.isAmrap,
                            onClick = onToggleAmrap
                        )
                    }
                }
            }
        }
    }
}

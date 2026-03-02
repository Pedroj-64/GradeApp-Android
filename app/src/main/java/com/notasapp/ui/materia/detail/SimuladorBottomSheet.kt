package com.notasapp.ui.materia.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notasapp.domain.model.Componente
import com.notasapp.domain.usecase.SimularNotaUseCase

/**
 * Bottom sheet "¿Qué pasa si saco X?".
 *
 * Permite al usuario ingresar notas hipotéticas para los componentes
 * sin nota y ver la nota final proyectada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimuladorBottomSheet(
    componentesFaltantes: List<Componente>,
    escalaMax: Float,
    notaAprobacion: Float,
    resultado: SimularNotaUseCase.Resultado?,
    onSimular: (Map<Long, Float>) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        SimuladorContent(
            componentesFaltantes = componentesFaltantes,
            escalaMax = escalaMax,
            notaAprobacion = notaAprobacion,
            resultado = resultado,
            onSimular = onSimular,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun SimuladorContent(
    componentesFaltantes: List<Componente>,
    escalaMax: Float,
    notaAprobacion: Float,
    resultado: SimularNotaUseCase.Resultado?,
    onSimular: (Map<Long, Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val notasInputs = remember { mutableStateMapOf<Long, String>() }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "¿Qué pasa si saco...?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Ingresa notas hipotéticas para los componentes pendientes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        if (componentesFaltantes.isEmpty()) {
            Text(
                text = "Todos los componentes ya tienen nota.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            // Inputs por componente faltante
            componentesFaltantes.forEach { comp ->
                OutlinedTextField(
                    value = notasInputs[comp.id] ?: "",
                    onValueChange = { raw ->
                        val sanitized = raw.filter { it.isDigit() || it == '.' }
                        if (sanitized.count { it == '.' } <= 1) {
                            notasInputs[comp.id] = sanitized
                        }
                    },
                    label = { Text("${comp.nombre} (${kotlin.math.round(comp.porcentaje * 100).toInt()}%)") },
                    placeholder = { Text("0 - ${"%.1f".format(escalaMax)}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val notasMap = componentesFaltantes.associate { comp ->
                        val valor = notasInputs[comp.id]?.toFloatOrNull() ?: 0f
                        comp.id to valor.coerceIn(0f, escalaMax)
                    }
                    onSimular(notasMap)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Simular")
            }
        }

        // Resultado animado
        AnimatedContent(
            targetState = resultado,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "sim_result"
        ) { res ->
            if (res != null) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    SimuladorResultCard(
                        resultado = res,
                        notaAprobacion = notaAprobacion
                    )
                }
            }
        }
    }
}

@Composable
private fun SimuladorResultCard(
    resultado: SimularNotaUseCase.Resultado,
    notaAprobacion: Float
) {
    val esAprobado = resultado.notaFinalProyectada >= notaAprobacion

    val icon = if (esAprobado) Icons.Default.CheckCircle else Icons.Default.Warning
    val title = "Nota final proyectada: ${"%.2f".format(resultado.notaFinalProyectada)}"
    val subtitle = if (esAprobado)
        "¡Con estas notas aprobarías! 🎉"
    else
        "Con estas notas no alcanzarías el mínimo de ${"%.2f".format(notaAprobacion)}"

    val containerColor: Color
    val contentColor: Color
    if (esAprobado) {
        containerColor = MaterialTheme.colorScheme.secondaryContainer
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        containerColor = MaterialTheme.colorScheme.errorContainer
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(28.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f)
            )
            if (resultado.componentesSimulados.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                resultado.componentesSimulados.forEach { cs ->
                    Text(
                        text = "· ${cs.nombre}: ${"%.2f".format(cs.notaHipotetica)} → aporte: ${"%.2f".format(cs.aporteSimulado)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

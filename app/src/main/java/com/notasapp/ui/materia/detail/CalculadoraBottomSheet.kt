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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notasapp.domain.usecase.CalcularNotaNecesariaUseCase

/**
 * Bottom sheet que permite al usuario ingresar una meta de nota final y
 * ver qué promedio necesita obtener en las evaluaciones pendientes.
 *
 * Consume [MateriaDetailViewModel.notaNecesariaResult] y llama a
 * [MateriaDetailViewModel.calcularNotaNecesaria] al presionar "Calcular".
 *
 * @param escalaMax          Nota máxima de la materia (para validación y hints).
 * @param resultado          Resultado actual del caso de uso (puede ser null = sin calcular).
 * @param onCalcular         Callback con la meta ingresada por el usuario.
 * @param onDismiss          Cierra el sheet (y limpia el estado con clearCalculadora).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculadoraBottomSheet(
    escalaMax: Float,
    resultado: CalcularNotaNecesariaUseCase.Resultado?,
    onCalcular: (Float) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDragHandle() }
    ) {
        CalculadoraContent(
            escalaMax = escalaMax,
            resultado = resultado,
            onCalcular = onCalcular,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

// ── Handle ──────────────────────────────────────────────────────────────────

@Composable
private fun BottomSheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}

// ── Contenido principal ──────────────────────────────────────────────────────

@Composable
private fun CalculadoraContent(
    escalaMax: Float,
    resultado: CalcularNotaNecesariaUseCase.Resultado?,
    onCalcular: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var inputMeta by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Calculate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "¿Qué nota necesito?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Ingresa la nota final que deseas alcanzar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // Input meta
        OutlinedTextField(
            value = inputMeta,
            onValueChange = { raw ->
                inputError = null
                // Solo números y un punto decimal
                val sanitized = raw.filter { it.isDigit() || it == '.' }
                if (sanitized.count { it == '.' } <= 1) inputMeta = sanitized
            },
            label = { Text("Meta final (máx ${"%.1f".format(escalaMax)})") },
            singleLine = true,
            isError = inputError != null,
            supportingText = inputError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    calcular(inputMeta, escalaMax, onCalcular) { inputError = it }
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                keyboard?.hide()
                calcular(inputMeta, escalaMax, onCalcular) { inputError = it }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Calcular")
        }

        // Resultado animado
        AnimatedContent(
            targetState = resultado,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "calc_result"
        ) { res ->
            if (res != null) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    ResultadoCard(resultado = res)
                }
            }
        }
    }
}

// ── Tarjeta de resultado ────────────────────────────────────────────────────

@Composable
private fun ResultadoCard(resultado: CalcularNotaNecesariaUseCase.Resultado) {
    when (resultado) {
        is CalcularNotaNecesariaUseCase.Resultado.MetaYaAlcanzada -> {
            ResultBanner(
                icon = Icons.Default.CheckCircle,
                title = "¡Meta ya alcanzada!",
                subtitle = if (resultado.aprobado) "Ya tienes nota suficiente para aprobar." else "Ya cumpliste tu meta propuesta.",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        is CalcularNotaNecesariaUseCase.Resultado.Posible -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultBanner(
                    icon = Icons.Default.TrendingUp,
                    title = "Necesitas: ${"%.2f".format(resultado.notaNecesaria)}",
                    subtitle = "Promedio en las ${resultado.componentesFaltantes} evaluación(es) pendiente(s).",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        is CalcularNotaNecesariaUseCase.Resultado.Imposible -> {
            ResultBanner(
                icon = Icons.Default.Error,
                title = "Meta inalcanzable",
                subtitle = "Se necesitaría ${"%.2f".format(resultado.notaNecesariaCalculada)} puntos, lo cual supera la nota máxima.",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        is CalcularNotaNecesariaUseCase.Resultado.Error -> {
            ResultBanner(
                icon = Icons.Default.Info,
                title = "No se pudo calcular",
                subtitle = resultado.mensaje,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultBanner(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color
) {
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
        }
    }
}

// ── Helper ──────────────────────────────────────────────────────────────────

private fun calcular(
    input: String,
    escalaMax: Float,
    onCalcular: (Float) -> Unit,
    onError: (String) -> Unit
) {
    val meta = input.toFloatOrNull()
    when {
        meta == null -> onError("Ingresa una nota válida")
        meta <= 0f -> onError("La meta debe ser mayor que 0")
        meta > escalaMax -> onError("La meta no puede superar ${"%.1f".format(escalaMax)}")
        else -> onCalcular(meta)
    }
}

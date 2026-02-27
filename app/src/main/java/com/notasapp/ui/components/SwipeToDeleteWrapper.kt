package com.notasapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Wrapper que agrega gesto de swipe-to-delete a cualquier composable hijo.
 *
 * Al deslizar hacia la izquierda aparece un fondo rojo con ícono de papelera.
 * Cuando el swipe se completa, se llama [onDelete] y el item desaparece con
 * animación gracias a [AnimatedVisibility] en el padre (HomeScreen).
 *
 * @param onDelete  Callback ejecutado cuando el swipe alcanza el umbral.
 * @param content   Contenido a mostrar (por ejemplo, una [MateriaCard]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteWrapper(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SwipeToDismissBoxState) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    // Fondo con color animado según el progreso del swipe
    val backgroundColor by animateColorAsState(
        targetValue = when (dismissState.targetValue) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            else -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "swipe_bg_color"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.2f else 0.9f,
        animationSpec = tween(200),
        label = "swipe_icon_scale"
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,   // solo swipe izquierda
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = backgroundColor,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar materia",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.scale(iconScale)
                )
            }
        }
    ) {
        content(dismissState)
    }
}

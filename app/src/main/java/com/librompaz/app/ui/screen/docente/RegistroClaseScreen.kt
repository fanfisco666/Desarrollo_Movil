package com.librompaz.app.ui.screen.docente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.util.hoy
import com.librompaz.app.viewmodel.LibroMpazViewModel
import kotlinx.coroutines.launch

/**
 * Módulo "Cobertura curricular" (docente): registrar los OA y contenidos trabajados en una
 * sesión o bloque, y ver las sesiones ya registradas.
 */
@Composable
fun RegistroClaseScreen(navController: NavHostController, viewModel: LibroMpazViewModel) {
    val cursoId by viewModel.cursoSeleccionadoId.collectAsState()
    val asignaturaId by viewModel.asignaturaSeleccionadaId.collectAsState()
    val todas by viewModel.sesiones.collectAsState()
    val objetivos = viewModel.objetivosDe(asignaturaId)
    val registradas = remember(todas, cursoId, asignaturaId) { viewModel.sesionesDe(cursoId, asignaturaId) }

    var fecha by remember { mutableStateOf(hoy()) }
    var bloque by remember { mutableStateOf("") }
    var seleccionados by remember { mutableStateOf(setOf<String>()) }
    var contenido by remember { mutableStateOf("") }

    // --- NUEVO: Estado para el Snackbar y Animación ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mensajeViewModel by viewModel.errorSesion.collectAsState() // Viene del ViewModel de Matías

    Scaffold(
        topBar = { BarraSuperior("Registro de clase", navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) } // --- NUEVO: Recurso Nativo
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fecha,
                    onValueChange = { fecha = it },
                    label = { Text("Fecha") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = bloque,
                    onValueChange = { bloque = it },
                    label = { Text("Bloque") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Objetivos de Aprendizaje trabajados", style = MaterialTheme.typography.titleSmall)
            objetivos.forEach { oa ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = oa.id in seleccionados,
                        onCheckedChange = { marcado ->
                            seleccionados = if (marcado) seleccionados + oa.id else seleccionados - oa.id
                        }
                    )
                    Column {
                        Text(oa.codigo, style = MaterialTheme.typography.labelMedium)
                        Text(oa.descripcion, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = contenido,
                onValueChange = { contenido = it },
                label = { Text("Contenidos abordados") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // --- NUEVO: Animación del mensaje (Criterio G7 de la rúbrica) ---
            AnimatedVisibility(
                visible = mensajeViewModel != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val texto = mensajeViewModel ?: ""
                val esError = texto.contains("Error") || texto.contains("obligatorio") || texto.contains("Debes") || texto.contains("inválido")

                Text(
                    text = texto,
                    color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    // --- NUEVO: Llamada a la función de Room de Matías ---
                    viewModel.guardarSesionEnRoom(
                        cursoId.orEmpty(),
                        asignaturaId.orEmpty(),
                        fecha,
                        bloque.trim(),
                        seleccionados.toList(),
                        contenido.trim()
                    )

                    // Si el mensaje del ViewModel es de éxito, mostramos el Snackbar y limpiamos
                    if (viewModel.errorSesion.value == "¡Guardado con éxito en la base de datos!") {
                        scope.launch {
                            snackbarHostState.showSnackbar("Clase guardada con éxito")
                        }
                        bloque = ""
                        seleccionados = emptySet()
                        contenido = ""
                    }
                }
            ) { Text("Guardar clase") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Clases registradas (${registradas.size})", style = MaterialTheme.typography.titleSmall)
            registradas.forEach { sesion ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${sesion.fecha} · ${sesion.bloque}", style = MaterialTheme.typography.titleSmall)
                        Text(sesion.contenido, style = MaterialTheme.typography.bodySmall)
                        Text("OA: ${sesion.oaIds.size} · Registró: ${sesion.registradoPor}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
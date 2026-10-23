package com.librompaz.app.ui.screen.docente

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.util.hoy
import com.librompaz.app.viewmodel.LibroMpazViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resultado by viewModel.resultadoSesion.collectAsState()
    val context = LocalContext.current

    var mostrarCalendario by remember { mutableStateOf(false) }

    // RECURSO NATIVO 2: VIBRACIÓN (Feedback háptico)
    LaunchedEffect(resultado) {
        if (resultado != null) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (resultado?.exito == true) {
                // Vibrar en éxito
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
                scope.launch { snackbarHostState.showSnackbar(resultado?.mensaje ?: "Guardado exitoso") }
                bloque = ""
                seleccionados = emptySet()
                contenido = ""
                viewModel.limpiarResultadoSesion()
            } else {
                // Vibrar en error
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1)
                }
            }
        }
    }

    // Limpiar error al salir de la pantalla
    DisposableEffect(Unit) {
        onDispose { viewModel.limpiarResultadoSesion() }
    }

    if (mostrarCalendario) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // FIX DE CÉSAR: Zona horaria UTC
                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        fecha = formatter.format(Date(millis))
                    }
                    mostrarCalendario = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = { BarraSuperior("Registro de clase", navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = fecha,
                    onValueChange = { fecha = it },
                    label = { Text("Fecha") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { mostrarCalendario = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Abrir calendario")
                        }
                    },
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

            AnimatedVisibility(
                visible = resultado != null && resultado?.exito == false,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = resultado?.mensaje ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.guardarSesionEnRoom(
                        cursoId.orEmpty(), asignaturaId.orEmpty(), fecha, bloque.trim(), seleccionados.toList(), contenido.trim()
                    )
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
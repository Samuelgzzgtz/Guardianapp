package com.example.gab.ui.admin.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.*
import com.example.gab.data.remote.SupabaseClientProvider
import com.example.gab.data.repository.AdminRepository
import com.example.gab.data.repository.AuthRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class CreateUserState {
    object Idle    : CreateUserState()
    object Loading : CreateUserState()
    object Success : CreateUserState()
    data class Error(val message: String) : CreateUserState()
}

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val repo     = AdminRepository()
    private val authRepo = AuthRepository(application)

    private val _stats        = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    private val _usuarios     = MutableStateFlow<List<Usuario>>(emptyList())
    val usuarios: StateFlow<List<Usuario>> = _usuarios.asStateFlow()

    private val _reportes     = MutableStateFlow<List<Reporte>>(emptyList())
    val reportes: StateFlow<List<Reporte>> = _reportes.asStateFlow()

    private val _reservas     = MutableStateFlow<List<Reserva>>(emptyList())
    val reservas: StateFlow<List<Reserva>> = _reservas.asStateFlow()

    private val _unidades     = MutableStateFlow<List<Unidad>>(emptyList())
    val unidades: StateFlow<List<Unidad>> = _unidades.asStateFlow()

    private val _unidadesConEstatus = MutableStateFlow<List<UnidadConEstatus>>(emptyList())
    val unidadesConEstatus: StateFlow<List<UnidadConEstatus>> = _unidadesConEstatus.asStateFlow()

    private val _avisos        = MutableStateFlow<List<Aviso>>(emptyList())
    val avisos: StateFlow<List<Aviso>> = _avisos.asStateFlow()

    private val _morosos      = MutableStateFlow<List<MorosoRow>>(emptyList())
    val morosos: StateFlow<List<MorosoRow>> = _morosos.asStateFlow()

    private val _cuotasUsuario = MutableStateFlow<List<Cuota>>(emptyList())
    val cuotasUsuario: StateFlow<List<Cuota>> = _cuotasUsuario.asStateFlow()

    private val _tareasLimpieza = MutableStateFlow<List<TareaLimpieza>>(emptyList())
    val tareasLimpieza: StateFlow<List<TareaLimpieza>> = _tareasLimpieza.asStateFlow()

    private val _amenidades = MutableStateFlow<List<Amenidad>>(emptyList())
    val amenidades: StateFlow<List<Amenidad>> = _amenidades.asStateFlow()

    private var selectedLimpiezaUserId: Int? = null

    private val _isLoading    = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _createState  = MutableStateFlow<CreateUserState>(CreateUserState.Idle)
    val createState: StateFlow<CreateUserState> = _createState.asStateFlow()

    private var realtimeJob: Job? = null
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getEstadisticas().onSuccess { _stats.value    = it }
            repo.getUsuarios().onSuccess     { _usuarios.value = it }
            repo.getReportes().onSuccess     { _reportes.value = it }
            repo.getReservas().onSuccess     { _reservas.value = it }
            repo.getUnidades().onSuccess           { _unidades.value          = it }
            repo.getUnidadesConEstatus().onSuccess { _unidadesConEstatus.value = it }
            repo.getMorosos().onSuccess      { _morosos.value  = it }
            repo.getAvisos().onSuccess       { _avisos.value   = it }
            repo.getAmenidades().onSuccess   { _amenidades.value = it }
            _isLoading.value = false
        }
        startRealtime()
    }

    private fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val channel = SupabaseClientProvider.client.channel("admin-live")
            realtimeChannel = channel
            val reporteChanges  = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "reporte" }
            val cleaningChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "tarealimpieza" }
            val reservaChanges  = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "reserva" }
            val cuotaChanges    = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "cuota" }
            channel.subscribe()
            launch {
                reporteChanges.collect {
                    repo.getReportes().onSuccess     { _reportes.value = it }
                    repo.getEstadisticas().onSuccess { _stats.value    = it }
                }
            }
            launch {
                cleaningChanges.collect {
                    selectedLimpiezaUserId?.let { uid ->
                        repo.getTareasLimpieza(uid).onSuccess { _tareasLimpieza.value = it }
                    }
                }
            }
            launch {
                reservaChanges.collect {
                    repo.getReservas().onSuccess     { _reservas.value = it }
                    repo.getEstadisticas().onSuccess { _stats.value    = it }
                }
            }
            launch {
                cuotaChanges.collect {
                    repo.getEstadisticas().onSuccess { _stats.value   = it }
                    repo.getMorosos().onSuccess       { _morosos.value = it }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        viewModelScope.launch {
            realtimeChannel?.let { runCatching { it.unsubscribe() } }
        }
    }

    fun filtrarUsuarios(rol: Int?) {
        viewModelScope.launch {
            repo.getUsuarios(rol).onSuccess { _usuarios.value = it }
        }
    }

    fun actualizarRol(userId: Int, rolId: Int) {
        viewModelScope.launch {
            repo.actualizarRolUsuario(userId, rolId)
                .onSuccess { _toastMessage.value = "Rol actualizado"; repo.getUsuarios().onSuccess { _usuarios.value = it } }
                .onFailure { _toastMessage.value = "Error: ${it.message}" }
        }
    }

    fun actualizarEstatusReporte(reporteId: Int, estatus: String) {
        viewModelScope.launch {
            repo.actualizarEstatusReporte(reporteId, estatus)
                .onSuccess { _toastMessage.value = "Reporte → $estatus"; repo.getReportes().onSuccess { _reportes.value = it } }
                .onFailure { _toastMessage.value = "Error: ${it.message}" }
        }
    }

    fun crearUnidad(numero: String, torre: String?, piso: Int, tipo: String) {
        viewModelScope.launch {
            repo.crearUnidad(numero, torre, piso, tipo)
                .onSuccess { _toastMessage.value = "Unidad $numero creada"; repo.getUnidades().onSuccess { _unidades.value = it } }
                .onFailure { _toastMessage.value = "Error: ${it.message}" }
        }
    }

    fun darDeBajaUnidad(unidadId: Int, numero: String) {
        viewModelScope.launch {
            if (repo.tieneCuotasPendientes(unidadId).getOrDefault(false)) {
                _toastMessage.value = "No se puede dar de baja: hay cuotas pendientes"; return@launch
            }
            repo.darDeBajaUnidad(unidadId)
                .onSuccess { _toastMessage.value = "Unidad $numero dada de baja"; repo.getUnidades().onSuccess { _unidades.value = it } }
                .onFailure { _toastMessage.value = "Error: ${it.message}" }
        }
    }

    fun asignarResidente(userId: Int, unidadId: Int?) {
        viewModelScope.launch {
            repo.asignarResidente(userId, unidadId)
                .onSuccess {
                    _toastMessage.value = if (unidadId != null) "Residente asignado" else "Asignación removida"
                    repo.getUsuarios().onSuccess { _usuarios.value = it }
                }
                .onFailure { _toastMessage.value = "Error: ${it.message}" }
        }
    }

    fun crearUsuario(nombre: String, apellido: String, email: String, password: String, rolId: Int, unidadId: Int?) {
        viewModelScope.launch {
            _createState.value = CreateUserState.Loading
            val nombreCompleto = "$nombre $apellido".trim()
            authRepo.createUser(nombreCompleto, email, password, rolId, unidadId)
                .onSuccess {
                    _createState.value = CreateUserState.Success
                    _toastMessage.value = "Usuario $nombreCompleto creado"
                    repo.getUsuarios().onSuccess           { _usuarios.value          = it }
                    repo.getUnidadesConEstatus().onSuccess { _unidadesConEstatus.value = it }
                }
                .onFailure { Log.e("AdminVM", "crearUsuario error", it); _createState.value = CreateUserState.Error(it.message ?: "Error al crear usuario") }
        }
    }

    fun eliminarUsuario(userId: Int, email: String, nombre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.eliminarUsuarioCompleto(userId, email)
                .onSuccess {
                    _toastMessage.value = "$nombre eliminado"
                    repo.getUsuarios().onSuccess { _usuarios.value = it }
                    repo.getUnidadesConEstatus().onSuccess { _unidadesConEstatus.value = it }
                }
                .onFailure { _toastMessage.value = "Error al eliminar: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun resetCreateState() { _createState.value = CreateUserState.Idle }

    fun loadCuotasUsuario(userId: Int) {
        viewModelScope.launch { repo.getCuotasPorUsuario(userId).onSuccess { _cuotasUsuario.value = it } }
    }

    fun loadTareasLimpieza(userId: Int) {
        selectedLimpiezaUserId = userId
        viewModelScope.launch {
            repo.getTareasLimpieza(userId).onSuccess { _tareasLimpieza.value = it }
        }
    }

    fun marcarCuotaPagada(cuotaId: Int) {
        viewModelScope.launch {
            repo.marcarCuotaPagada(cuotaId)
                .onSuccess {
                    loadUsuarios()
                    repo.getMorosos().onSuccess      { _morosos.value = it }
                    repo.getEstadisticas().onSuccess { _stats.value   = it }
                }
                .onFailure { Log.e("AdminVM", "marcarCuotaPagada error", it) }
        }
    }

    fun cobrarMensualidad(monto: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.cobrarMensualidad(monto)
                .onSuccess { count ->
                    _toastMessage.value = if (count > 0) "Mensualidad generada para $count residentes"
                                         else "Todos los residentes ya tienen cuota este mes"
                    repo.getEstadisticas().onSuccess { _stats.value   = it }
                    repo.getMorosos().onSuccess      { _morosos.value = it }
                }
                .onFailure { _toastMessage.value = "Error al cobrar: ${it.message}" }
            _isLoading.value = false
        }
    }

    private fun loadUsuarios() {
        viewModelScope.launch {
            repo.getUsuarios().onSuccess { _usuarios.value = it }
        }
    }

    fun crearTareaLimpieza(asignadoId: Int, titulo: String, area: String?, prioridad: String, notas: String?) {
        val fecha = java.time.LocalDate.now().toString()
        viewModelScope.launch {
            repo.crearTareaLimpieza(asignadoId, titulo, area, prioridad, fecha, notas)
                .onSuccess {
                    _toastMessage.value = "Tarea asignada"
                    repo.getTareasLimpieza(asignadoId).onSuccess { _tareasLimpieza.value = it }
                }
                .onFailure { _toastMessage.value = "Error: ${it.message}" }
        }
    }

    fun crearAviso(titulo: String, descripcion: String?, tono: String) {
        viewModelScope.launch {
            repo.crearAviso(titulo, descripcion, tono)
                .onSuccess {
                    _toastMessage.value = "Aviso publicado"
                    repo.getAvisos().onSuccess { _avisos.value = it }
                }
                .onFailure { _toastMessage.value = "Error al publicar aviso: ${it.message}" }
        }
    }

    fun eliminarAviso(avisoId: Int) {
        viewModelScope.launch {
            repo.eliminarAviso(avisoId)
                .onSuccess { _avisos.value = _avisos.value.filter { it.id != avisoId } }
                .onFailure { _toastMessage.value = "Error al eliminar aviso" }
        }
    }

    fun dispararRecordatorioPago() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.dispararRecordatorioPago()
                .onSuccess { count -> _toastMessage.value = "Recordatorios enviados: $count usuarios" }
                .onFailure { _toastMessage.value = "Error al enviar recordatorios: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun clearToast() { _toastMessage.value = null }
}

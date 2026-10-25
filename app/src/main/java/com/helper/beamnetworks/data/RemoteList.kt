package com.helper.beamnetworks.data

import com.helper.beamnetworks.data.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A list loaded from the API, with loading/error flags for the UI. */
data class Loadable<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val moduleDisabled: Boolean = false,
)

/**
 * Holds one API-backed list. [refresh] reloads it (concurrent calls share one load), and
 * [update] applies a local change after a successful write so the UI updates immediately.
 */
class RemoteList<T>(private val scope: CoroutineScope, private val loader: suspend () -> List<T>) {
    private val _state = MutableStateFlow(Loadable<T>())
    val state: StateFlow<Loadable<T>> = _state.asStateFlow()
    private val mutex = Mutex()
    private var inFlight: Job? = null

    val items: List<T> get() = _state.value.items

    suspend fun refresh() {
        mutex.withLock {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val items = loader()
                _state.value = Loadable(items, loading = false, loaded = true)
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, loaded = true, error = e.message, moduleDisabled = e.isModuleDisabled) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(loading = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loaded = true, error = e.message ?: "Something went wrong") }
            }
        }
    }

    /** Starts a refresh unless one is already running. */
    fun refreshAsync() {
        if (inFlight?.isActive == true) return
        inFlight = scope.launch { refresh() }
    }

    /** Loads once; later calls do nothing until [reset]. */
    fun ensureLoaded() {
        if (!_state.value.loaded && !_state.value.loading) refreshAsync()
    }

    fun update(transform: (List<T>) -> List<T>) {
        _state.update { it.copy(items = transform(it.items)) }
    }

    fun reset() {
        inFlight?.cancel()
        _state.value = Loadable()
    }
}

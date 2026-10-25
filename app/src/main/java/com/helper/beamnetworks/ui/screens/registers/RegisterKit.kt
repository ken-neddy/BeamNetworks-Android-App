package com.helper.beamnetworks.ui.screens.registers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.Page
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.InputBox
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.TextLink
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A searchable, filterable server-side list (payments, invoices, vouchers, SMS log…). Loads page 1
 * on [start] and whenever the query or filter changes; [loadMore] appends the next page.
 */
abstract class RegisterViewModel<T, F>(val app: AppContainer, initialFilter: F) : ViewModel() {
    var query by mutableStateOf("")
        private set
    var filter by mutableStateOf(initialFilter)
        private set
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var total by mutableStateOf<Int?>(null)
        private set

    /** Key of the action in progress, so its button can show "…". */
    var busy by mutableStateOf<String?>(null)
        private set

    private var page = 1
    private var job: Job? = null
    private var started = false

    protected abstract suspend fun fetch(page: Int, query: String, filter: F): Page<T>

    fun start() {
        if (started) return
        started = true
        reload()
    }

    fun search(value: String) {
        query = value
        reload(debounceMs = 350)
    }

    fun selectFilter(value: F) {
        if (value == filter) return
        filter = value
        reload()
    }

    fun reload(debounceMs: Long = 0) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            loading = true
            error = null
            try {
                val result = apiCall { fetch(1, query.trim(), filter) }
                items = result.results
                total = result.count
                page = 1
                hasMore = result.next != null
            } catch (e: ApiException) {
                error = e.message
            } finally {
                loading = false
                loaded = true
            }
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        job = viewModelScope.launch {
            loading = true
            try {
                val result = apiCall { fetch(page + 1, query.trim(), filter) }
                items = items + result.results
                page += 1
                hasMore = result.next != null
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't load more")
            } finally {
                loading = false
            }
        }
    }

    /** Adds a newly created row at the top. */
    protected fun prepend(item: T) {
        items = listOf(item) + items
    }

    /** Swaps in an updated row after an action. */
    protected fun replace(match: (T) -> Boolean, updated: T) {
        items = items.map { if (match(it)) updated else it }
    }

    /** Runs a write with a busy flag and a toast for success or failure. */
    protected fun act(key: String, success: String?, block: suspend () -> Unit) {
        if (busy != null) return
        busy = key
        viewModelScope.launch {
            try {
                block()
                success?.let(app.toasts::show)
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Something went wrong")
            } finally {
                busy = null
            }
        }
    }
}

/** Search box, filter pills and the list with loading, empty, error and "Load more" states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T, F> RegisterList(
    vm: RegisterViewModel<T, F>,
    filters: List<PillTabOption<F>>,
    searchHint: String?,
    emptyTitle: String,
    emptyMessage: String,
    key: (T) -> Any,
    header: LazyListScope.() -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    LaunchedEffect(Unit) { vm.start() }
    PullToRefreshBox(isRefreshing = vm.loading && vm.loaded && vm.items.isNotEmpty(), onRefresh = { vm.reload() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            header()
            if (searchHint != null) {
                item(key = "search") {
                    InputBox(
                        value = vm.query,
                        onValueChange = vm::search,
                        placeholder = searchHint,
                        height = 46.dp,
                        fill = BeamColors.Surface,
                        shape = BeamShapes.Button,
                        leadingIcon = BeamIcons.Search,
                    )
                }
            }
            if (filters.isNotEmpty()) {
                item(key = "filters") {
                    PillTabs(options = filters, selected = vm.filter, onSelect = vm::selectFilter, scrollable = true, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            when {
                vm.error != null && vm.items.isEmpty() -> item(key = "error") {
                    EmptyState("Couldn't load", vm.error!!, actionLabel = "Try again", onAction = { vm.reload() }, topPadding = 40.dp)
                }
                !vm.loaded || (vm.loading && vm.items.isEmpty()) -> item(key = "loading") {
                    Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(12.dp))
                }
                vm.items.isEmpty() -> item(key = "empty") { EmptyState(emptyTitle, emptyMessage, topPadding = 40.dp) }
            }
            items(vm.items, key = key) { row(it) }
            if (vm.hasMore) {
                item(key = "more") {
                    TextLink(if (vm.loading) "Loading…" else "Load more", vm::loadMore, modifier = Modifier.padding(8.dp), style = BeamType.BodyStrong)
                }
            }
            item(key = "bottom") { BottomSpacer() }
        }
    }
}

/** Yes/no check before a destructive or money-moving action. */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BeamColors.Surface,
        title = { Text(title, style = BeamType.SheetTitle) },
        text = { Text(message, style = BeamType.Small.copy(color = BeamColors.TextSecondary)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, style = BeamType.BodyStrong.copy(color = if (danger) BeamColors.Danger else BeamColors.Primary)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = BeamType.BodyStrong.copy(color = BeamColors.TextSecondary)) } },
    )
}

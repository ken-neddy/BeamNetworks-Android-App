package com.helper.beamnetworks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.helper.beamnetworks.BeamNetworksApplication
import com.helper.beamnetworks.CallLogScreen
import com.helper.beamnetworks.MapScreen
import com.helper.beamnetworks.MessagePickerScreen
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.ui.components.BeamBottomBar
import com.helper.beamnetworks.ui.components.BeamToast
import com.helper.beamnetworks.ui.components.BeamTopBar
import com.helper.beamnetworks.ui.components.BottomTab
import com.helper.beamnetworks.ui.navigation.AccountRoute
import com.helper.beamnetworks.ui.navigation.AccountsRoute
import com.helper.beamnetworks.ui.navigation.CallLogRoute
import com.helper.beamnetworks.ui.navigation.CustomerDetailRoute
import com.helper.beamnetworks.ui.navigation.CustomersRoute
import com.helper.beamnetworks.ui.navigation.HomeRoute
import com.helper.beamnetworks.ui.navigation.InstallDetailRoute
import com.helper.beamnetworks.ui.navigation.InstallsRoute
import com.helper.beamnetworks.ui.navigation.MapRoute
import com.helper.beamnetworks.ui.navigation.MessagePickerRoute
import com.helper.beamnetworks.ui.navigation.NetworkRoute
import com.helper.beamnetworks.ui.navigation.NotificationSettingsRoute
import com.helper.beamnetworks.ui.navigation.NotificationsRoute
import com.helper.beamnetworks.ui.navigation.StockRoute
import com.helper.beamnetworks.ui.navigation.TicketsRoute
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.screens.account.AccountScreen
import com.helper.beamnetworks.ui.screens.account.AccountsScreen
import com.helper.beamnetworks.ui.screens.account.StockScreen
import com.helper.beamnetworks.ui.screens.customers.CustomerDetailScreen
import com.helper.beamnetworks.ui.screens.customers.CustomersScreen
import com.helper.beamnetworks.ui.screens.home.HomeScreen
import com.helper.beamnetworks.ui.screens.installs.InstallDetailScreen
import com.helper.beamnetworks.ui.screens.installs.InstallsScreen
import com.helper.beamnetworks.ui.screens.login.LoginScreen
import com.helper.beamnetworks.ui.screens.notifications.AnnouncementsRoute
import com.helper.beamnetworks.ui.screens.notifications.AnnouncementsScreen
import com.helper.beamnetworks.ui.screens.notifications.CustomerMapRoute
import com.helper.beamnetworks.ui.screens.notifications.CustomerMapScreen
import com.helper.beamnetworks.ui.screens.notifications.NetworkScreen
import com.helper.beamnetworks.ui.screens.notifications.SessionsRoute
import com.helper.beamnetworks.ui.screens.notifications.SessionsScreen
import com.helper.beamnetworks.ui.screens.notifications.NotificationSettingsScreen
import com.helper.beamnetworks.ui.screens.notifications.NotificationsScreen
import com.helper.beamnetworks.ui.screens.tickets.TicketDetailRoute
import com.helper.beamnetworks.ui.screens.tickets.TicketDetailScreen
import com.helper.beamnetworks.ui.screens.tickets.TicketsScreen
import com.helper.beamnetworks.ui.sheets.Picker
import com.helper.beamnetworks.ui.sheets.SheetDestination
import com.helper.beamnetworks.ui.sheets.SheetHost
import com.helper.beamnetworks.ui.sheets.SheetType
import com.helper.beamnetworks.ui.sheets.SheetViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Signs in first, then shows the redesigned app. */
@Composable
fun BeamApp() {
    val app = (LocalContext.current.applicationContext as BeamNetworksApplication).container
    val session by app.sessions.session.collectAsStateWithLifecycle()
    if (session == null) LoginScreen() else BeamShell(app)
}

private val PickerKeys = listOf("selected_phone_number", "selected_client_name", "picked_location", "picked_amount")

@Composable
private fun BeamShell(app: AppContainer) {
    val nav = rememberNavController()
    val sheets = containerViewModel(key = "sheets") { container, _ -> SheetViewModel(container) }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val homeList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val toast by app.toasts.message.collectAsStateWithLifecycle()

    val feed by app.notificationFeed.items.collectAsStateWithLifecycle()
    val lastRead by app.notificationFeed.lastRead.collectAsStateWithLifecycle()
    val hasUnread = feed.any { it.time > lastRead }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val isPicker = destination.isAny(CallLogRoute::class, MapRoute::class, MessagePickerRoute::class)
    val isSub = destination.isAny(
        CustomerDetailRoute::class, InstallDetailRoute::class, StockRoute::class, AccountsRoute::class,
        NotificationsRoute::class, NotificationSettingsRoute::class, NetworkRoute::class, TicketDetailRoute::class,
        SessionsRoute::class, CustomerMapRoute::class, AnnouncementsRoute::class,
    )
    val tab = when {
        destination.isAny(HomeRoute::class) -> BottomTab.Home
        destination.isAny(CustomersRoute::class, CustomerDetailRoute::class) -> BottomTab.Customers
        destination.isAny(InstallsRoute::class, InstallDetailRoute::class) -> BottomTab.Installs
        destination.isAny(TicketsRoute::class, TicketDetailRoute::class) -> BottomTab.Tickets
        else -> null
    }

    // No push channel for staff exists, so check for new events every minute while the app is open
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                runCatching { app.notificationChecker.check() }
                delay(60_000)
            }
        }
    }
    LaunchedEffect(Unit) {
        launch { sheets.destinations.collect { nav.goTo(it) } }
        launch {
            sheets.pickers.collect { picker ->
                nav.navigate(
                    when (picker) {
                        Picker.CallLog -> CallLogRoute
                        Picker.Map -> MapRoute
                        Picker.Sms -> MessagePickerRoute
                    },
                )
            }
        }
    }
    // When a picker closes, hand its result to the parked sheet and reopen it
    LaunchedEffect(entry) {
        val current = entry ?: return@LaunchedEffect
        if (sheets.parkedSheet == null || isPicker) return@LaunchedEffect
        val handle = current.savedStateHandle
        val results = PickerKeys.associateWith { key -> handle.get<Any>(key)?.toString().also { handle.remove<Any>(key) } }
        sheets.resumeAfterPicker(results)
    }

    Column(Modifier.fillMaxSize().background(BeamColors.Background)) {
        if (!isPicker) {
            BeamTopBar(
                title = titleFor(destination),
                showBack = isSub,
                showAlertDot = hasUnread,
                onBack = { nav.popBackStack() },
                onBell = { nav.navigate(NotificationsRoute) { launchSingleTop = true } },
                onAccount = { nav.goToTab(AccountRoute) },
            )
        }
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        listState = homeList,
                        onAccounts = { nav.navigate(AccountsRoute) },
                        onCustomers = { filter -> nav.goToTab(CustomersRoute(filter), restore = filter == null) },
                        onTickets = { nav.goToTab(TicketsRoute("open"), restore = false) },
                        onInstalls = { nav.goToTab(InstallsRoute("upcoming"), restore = false) },
                        onInstall = { nav.navigate(InstallDetailRoute(it)) },
                        onStock = { nav.navigate(StockRoute) },
                    )
                }
                composable<CustomersRoute> { backStack ->
                    CustomersScreen(backStack.toRoute<CustomersRoute>().filter) { nav.navigate(CustomerDetailRoute(it)) }
                }
                composable<CustomerDetailRoute>(deepLinks = listOf(navDeepLink<CustomerDetailRoute>(basePath = "beamnetworks://app/customer"))) {
                    CustomerDetailScreen(
                        onRecordPayment = { sheets.open(SheetType.Payment(it)) },
                        onRestore = { sheets.open(SheetType.Extend(it)) },
                        onRaiseTicket = { sheets.open(SheetType.Ticket(it)) },
                    )
                }
                composable<InstallsRoute> { backStack ->
                    InstallsScreen(
                        segmentKey = backStack.toRoute<InstallsRoute>().segment,
                        onOpen = { nav.navigate(InstallDetailRoute(it)) },
                        onMarkDone = { sheets.open(SheetType.CompleteJob(it)) },
                        onSchedule = { sheets.open(SheetType.Job()) },
                    )
                }
                composable<InstallDetailRoute>(
                    // Matches the link in installation reminder notifications
                    deepLinks = listOf(navDeepLink<InstallDetailRoute>(basePath = "beamnetworks://app/installation_details")),
                ) {
                    InstallDetailScreen(
                        onEdit = { sheets.open(SheetType.Job(it)) },
                        onMarkDone = { sheets.open(SheetType.CompleteJob(it)) },
                    )
                }
                composable<TicketsRoute>(deepLinks = listOf(navDeepLink<TicketsRoute>(basePath = "beamnetworks://app/tickets"))) { backStack ->
                    TicketsScreen(backStack.toRoute<TicketsRoute>().segment, onRaise = { sheets.open(SheetType.Ticket()) }, onOpen = { nav.navigate(TicketDetailRoute(it)) })
                }
                composable<TicketDetailRoute>(deepLinks = listOf(navDeepLink<TicketDetailRoute>(basePath = "beamnetworks://app/ticket"))) {
                    TicketDetailScreen()
                }
                composable<AccountRoute>(deepLinks = listOf(navDeepLink<AccountRoute>(basePath = "beamnetworks://app/account"))) {
                    AccountScreen(
                        onNotifications = { nav.navigate(NotificationSettingsRoute) },
                        onNetwork = { nav.navigate(NetworkRoute) },
                        onStock = { nav.navigate(StockRoute) },
                        onAccounts = { nav.navigate(AccountsRoute) },
                        onSessions = { nav.navigate(SessionsRoute) },
                        onAnnouncements = { nav.navigate(AnnouncementsRoute) },
                        onCallLog = { nav.navigate(CallLogRoute) },
                        onMap = { nav.navigate(MapRoute) },
                        onMessages = { nav.navigate(MessagePickerRoute) },
                    )
                }
                composable<StockRoute>(deepLinks = listOf(navDeepLink<StockRoute>(basePath = "beamnetworks://app/stock"))) { StockScreen { sheets.open(SheetType.Product(it)) } }
                composable<NotificationsRoute>(deepLinks = listOf(navDeepLink<NotificationsRoute>(basePath = "beamnetworks://app/notifications"))) {
                    NotificationsScreen(
                        onOpenLink = { link -> runCatching { nav.navigate(link.toUri()) } },
                        onSettings = { nav.navigate(NotificationSettingsRoute) },
                    )
                }
                composable<NotificationSettingsRoute> { NotificationSettingsScreen() }
                composable<NetworkRoute>(deepLinks = listOf(navDeepLink<NetworkRoute>(basePath = "beamnetworks://app/network"))) {
                    NetworkScreen(
                        onCustomer = { nav.navigate(CustomerDetailRoute(it)) },
                        onSessions = { nav.navigate(SessionsRoute) },
                        onMap = { nav.navigate(CustomerMapRoute) },
                    )
                }
                composable<SessionsRoute> { SessionsScreen { nav.navigate(CustomerDetailRoute(it)) } }
                composable<CustomerMapRoute> { CustomerMapScreen { nav.navigate(CustomerDetailRoute(it)) } }
                composable<AnnouncementsRoute> {
                    AnnouncementsScreen()
                }
                composable<AccountsRoute> { AccountsScreen { sheets.open(SheetType.Expense(it)) } }
                composable<CallLogRoute> { CallLogScreen(nav) }
                composable<MapRoute> { MapScreen(nav) }
                composable<MessagePickerRoute> { MessagePickerScreen(nav) }
            }
            if (sheets.sheet == null) BeamToast(toast, Modifier.align(Alignment.BottomCenter), bottom = 20.dp)
        }
        if (!isPicker) {
            BeamBottomBar(
                selected = tab,
                onSelect = { selected ->
                    nav.goToTab(
                        when (selected) {
                            BottomTab.Home -> HomeRoute
                            BottomTab.Customers -> CustomersRoute()
                            BottomTab.Installs -> InstallsRoute()
                            BottomTab.Tickets -> TicketsRoute()
                        },
                    )
                },
                onCreate = sheets::openCreateMenu,
            )
        }
    }

    SheetHost(sheets, onDeleteExpense = { id ->
        scope.launch {
            runCatching { app.expenses.delete(id) }
                .onSuccess {
                    sheets.close()
                    app.toasts.show("Expense deleted")
                }
                .onFailure { app.toasts.show(it.message ?: "Couldn't delete the expense") }
        }
    })
}

private fun NavDestination?.isAny(vararg routes: kotlin.reflect.KClass<*>): Boolean =
    this != null && routes.any { hasRoute(it) }

private fun titleFor(destination: NavDestination?): String = when {
    destination.isAny(HomeRoute::class) -> "Dashboard"
    destination.isAny(CustomersRoute::class) -> "Customers"
    destination.isAny(CustomerDetailRoute::class) -> "Customer details"
    destination.isAny(InstallsRoute::class) -> "Jobs"
    destination.isAny(InstallDetailRoute::class) -> "Job"
    destination.isAny(TicketsRoute::class) -> "Tickets"
    destination.isAny(TicketDetailRoute::class) -> "Ticket"
    destination.isAny(AccountRoute::class) -> "Account"
    destination.isAny(StockRoute::class) -> "Stock"
    destination.isAny(AccountsRoute::class) -> "Accounts"
    destination.isAny(NotificationsRoute::class) -> "Notifications"
    destination.isAny(NotificationSettingsRoute::class) -> "Notification settings"
    destination.isAny(NetworkRoute::class) -> "Network status"
    destination.isAny(SessionsRoute::class) -> "Live sessions"
    destination.isAny(CustomerMapRoute::class) -> "Customer map"
    destination.isAny(AnnouncementsRoute::class) -> "Announcements"
    else -> ""
}

/**
 * Switches top-level tab. [restore] brings back the tab's previous state; pass false when
 * arriving with a new filter (e.g. "Owing" from the dashboard) so the route's arguments apply.
 */
private fun NavHostController.goToTab(route: Any, restore: Boolean = true) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = restore }
        launchSingleTop = true
        restoreState = restore
    }
}

private fun NavHostController.goTo(destination: SheetDestination) = when (destination) {
    SheetDestination.Installs -> goToTab(InstallsRoute("upcoming"), restore = false)
    SheetDestination.Tickets -> goToTab(TicketsRoute("open"), restore = false)
    SheetDestination.Customers -> goToTab(CustomersRoute(), restore = false)
    SheetDestination.Accounts -> navigate(AccountsRoute) { launchSingleTop = true }
    SheetDestination.Stock -> navigate(StockRoute) { launchSingleTop = true }
}

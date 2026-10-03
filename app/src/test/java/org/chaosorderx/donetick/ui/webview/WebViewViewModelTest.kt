package org.chaosorderx.donetick.ui.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.chaosorderx.donetick.data.model.ServerConfig
import org.chaosorderx.donetick.data.sync.ChoreSyncCoordinator
import org.chaosorderx.donetick.domain.usecase.CheckServerConnectivityUseCase
import org.chaosorderx.donetick.domain.usecase.GetServerConfigUseCase
import org.chaosorderx.donetick.notification.ChoreNotificationManager
import org.chaosorderx.donetick.notification.SessionExpiryNotifier
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var vm: WebViewViewModel
    private lateinit var getServerConfig: GetServerConfigUseCase
    private lateinit var checkConnectivity: CheckServerConnectivityUseCase
    private lateinit var notifications: ChoreNotificationManager
    private lateinit var coordinator: ChoreSyncCoordinator
    private lateinit var sessionNotifier: SessionExpiryNotifier

    private fun chore(
        id: Int,
        name: String = "c$id",
        notification: Boolean = true,
        isActive: Boolean = true,
        nextDueDate: String? = "2035-01-01T00:00:00Z",
    ) = JSONObject().put("id", id).put("name", name)
        .put("notification", notification).put("isActive", isActive)
        .apply { if (nextDueDate != null) put("nextDueDate", nextDueDate) }

    private fun tokenReader(token: String?) = object : JwtReader {
        override suspend fun read(): String? = token
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        getServerConfig = mockk()
        checkConnectivity = mockk()
        notifications = mockk(relaxed = true)
        coordinator = mockk()
        sessionNotifier = mockk(relaxed = true)

        coEvery { getServerConfig.getCurrentConfig() } returns
            ServerConfig(url = "https://example.com", isConfigured = true, lastValidated = 1L)

        vm = WebViewViewModel(getServerConfig, checkConnectivity, notifications, coordinator, sessionNotifier)
        vm.jwtReader = tokenReader("jwt-token")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state is correct`() = runTest {
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        assertEquals("https://example.com", s.serverUrl)
        assertTrue(s.choresList.isEmpty())
    }

    @Test
    fun `unconfigured server navigates to setup`() = runTest {
        coEvery { getServerConfig.getCurrentConfig() } returns ServerConfig(url = "", isConfigured = false)
        val viewModel = WebViewViewModel(getServerConfig, checkConnectivity, notifications, coordinator, sessionNotifier)
        advanceUntilIdle()
        assertTrue(viewModel.navigationEvent.value is WebViewNavigationEvent.NavigateToSetup)
    }

    @Test
    fun `updateLoadingState, title, progress, canGoBack`() {
        vm.updateLoadingState(true); assertTrue(vm.uiState.value.isLoading)
        vm.updatePageTitle("T"); assertEquals("T", vm.uiState.value.pageTitle)
        vm.updateProgress(75); assertEquals(75, vm.uiState.value.progress)
        vm.updateCanGoBack(true); assertTrue(vm.uiState.value.canGoBack)
    }

    @Test
    fun `page finished with no token does not sync or schedule`() = runTest {
        vm.jwtReader = tokenReader(null)
        vm.onWebViewPageFinished()
        advanceUntilIdle()
        verify(exactly = 0) { notifications.scheduleChoreNotifications(any()) }
    }

    @Test
    fun `successful sync with changes updates list and schedules notifications`() = runTest {
        coEvery { coordinator.sync("jwt-token") } returns
            ChoreSyncCoordinator.Outcome.Success(listOf(chore(1, "Trash"), chore(2, "Dishes")), changed = true)

        vm.onWebViewPageFinished()
        advanceUntilIdle()

        assertEquals(listOf("Trash", "Dishes"), vm.uiState.value.choresList.map { it.name })
        verify { notifications.scheduleChoreNotifications(match { it.map { c -> c.id } == listOf(1, 2) }) }
    }

    @Test
    fun `non-notifiable chores are excluded from the list (stale one-offs, no due date, inactive)`() = runTest {
        coEvery { coordinator.sync(any()) } returns ChoreSyncCoordinator.Outcome.Success(
            listOf(
                chore(1, "Real"),
                chore(2, "No due date", nextDueDate = null),
                chore(3, "Completed one-off", isActive = false, nextDueDate = null),
                chore(4, "Notifications off", notification = false),
            ),
            changed = true,
        )

        vm.onWebViewPageFinished()
        advanceUntilIdle()

        assertEquals(listOf("Real"), vm.uiState.value.choresList.map { it.name })
        verify { notifications.scheduleChoreNotifications(match { it.map { c -> c.id } == listOf(1) }) }
    }

    @Test
    fun `unchanged sync after a first success does not reschedule`() = runTest {
        coEvery { coordinator.sync(any()) } returnsMany listOf(
            ChoreSyncCoordinator.Outcome.Success(listOf(chore(1)), changed = true),
            ChoreSyncCoordinator.Outcome.Success(listOf(chore(1)), changed = false),
        )

        vm.onWebViewPageFinished(); advanceUntilIdle()
        vm.onWebViewPageFinished(); advanceUntilIdle()

        verify(exactly = 1) { notifications.scheduleChoreNotifications(any()) }
    }

    @Test
    fun `unauthorized sync keeps the last-good list and does not crash`() = runTest {
        coEvery { coordinator.sync(any()) } returnsMany listOf(
            ChoreSyncCoordinator.Outcome.Success(listOf(chore(1, "Keep me")), changed = true),
            ChoreSyncCoordinator.Outcome.Unauthorized,
        )

        vm.onWebViewPageFinished(); advanceUntilIdle()
        vm.onWebViewPageFinished(); advanceUntilIdle()

        assertEquals(listOf("Keep me"), vm.uiState.value.choresList.map { it.name })
    }

    private class FakeSession(
        var expiry: Long?,
        val refreshedExpiry: Long? = null,
        val result: RefreshResult = RefreshResult.SUCCESS,
        val canRefresh: Boolean = true,
    ) : SessionBridge {
        var refreshCalls = 0
        override suspend fun readSession(): SessionInfo? = expiry?.let { SessionInfo(it, canRefresh) }
        override suspend fun refreshToken(): RefreshResult {
            refreshCalls++
            if (result == RefreshResult.SUCCESS && refreshedExpiry != null) expiry = refreshedExpiry
            return result
        }
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `token far from expiry is not refreshed and warning is armed`() = runTest {
        val expiry = System.currentTimeMillis() + 20 * day
        val session = FakeSession(expiry)
        vm.sessionBridge = session
        coEvery { coordinator.sync(any()) } returns ChoreSyncCoordinator.Outcome.Success(emptyList(), changed = false)

        vm.onWebViewPageFinished(); advanceUntilIdle()

        assertEquals(0, session.refreshCalls)
        verify { sessionNotifier.schedule(expiry, true) }
    }

    @Test
    fun `token inside the 5 day window is refreshed and warning moves to the new expiry`() = runTest {
        val newExpiry = System.currentTimeMillis() + 30 * day
        val session = FakeSession(System.currentTimeMillis() + 4 * day, refreshedExpiry = newExpiry)
        vm.sessionBridge = session
        coEvery { coordinator.sync(any()) } returns ChoreSyncCoordinator.Outcome.Success(emptyList(), changed = false)

        vm.onWebViewPageFinished(); advanceUntilIdle()

        assertEquals(1, session.refreshCalls)
        verify { sessionNotifier.schedule(newExpiry, true) }
    }

    @Test
    fun `failed refresh inside the window keeps the old expiry so the warning shows`() = runTest {
        val expiry = System.currentTimeMillis() + 2 * day
        val session = FakeSession(expiry, result = RefreshResult.FAILED)
        vm.sessionBridge = session
        coEvery { coordinator.sync(any()) } returns ChoreSyncCoordinator.Outcome.Success(emptyList(), changed = false)

        vm.onWebViewPageFinished(); advanceUntilIdle()

        verify { sessionNotifier.schedule(expiry, true) }
        coVerify { coordinator.sync("jwt-token") }
    }

    @Test
    fun `server without refresh support is never asked to refresh and warns to log in again`() = runTest {
        val expiry = System.currentTimeMillis() + 2 * day
        val session = FakeSession(expiry, canRefresh = false)
        vm.sessionBridge = session
        coEvery { coordinator.sync(any()) } returns ChoreSyncCoordinator.Outcome.Success(emptyList(), changed = false)

        vm.onWebViewPageFinished(); advanceUntilIdle()

        assertEquals(0, session.refreshCalls)
        verify { sessionNotifier.schedule(expiry, false) }
        coVerify { coordinator.sync("jwt-token") }
    }

    @Test
    fun `refresh endpoint missing on the server downgrades the warning to log in again`() = runTest {
        val expiry = System.currentTimeMillis() + 2 * day
        vm.sessionBridge = FakeSession(expiry, result = RefreshResult.UNSUPPORTED)
        coEvery { coordinator.sync(any()) } returns ChoreSyncCoordinator.Outcome.Success(emptyList(), changed = false)

        vm.onWebViewPageFinished(); advanceUntilIdle()

        verify { sessionNotifier.schedule(expiry, false) }
    }

    @Test
    fun `handleChoreMarkedDone cancels that chore's notification and marks it complete`() = runTest {
        coEvery { coordinator.sync(any()) } returns
            ChoreSyncCoordinator.Outcome.Success(listOf(chore(1), chore(2)), changed = true)
        vm.onWebViewPageFinished(); advanceUntilIdle()

        vm.handleChoreMarkedDone(1)

        verify { notifications.cancelChoreNotification(1) }
        assertTrue(vm.uiState.value.choresList.first { it.id == 1 }.isCompleted)
        assertFalse(vm.uiState.value.choresList.first { it.id == 2 }.isCompleted)
    }

    @Test
    fun `onNotificationPermissionGranted reschedules from the current list`() = runTest {
        coEvery { coordinator.sync(any()) } returns
            ChoreSyncCoordinator.Outcome.Success(listOf(chore(1)), changed = true)
        vm.onWebViewPageFinished(); advanceUntilIdle()

        vm.onNotificationPermissionGranted(); advanceUntilIdle()

        verify(atLeast = 2) { notifications.scheduleChoreNotifications(any()) }
    }

    @Test
    fun `clearNavigationEvent clears the event`() = runTest {
        coEvery { getServerConfig.getCurrentConfig() } returns ServerConfig(url = "", isConfigured = false)
        val viewModel = WebViewViewModel(getServerConfig, checkConnectivity, notifications, coordinator, sessionNotifier)
        advanceUntilIdle()
        assertNotNull(viewModel.navigationEvent.value)
        viewModel.clearNavigationEvent()
        assertNull(viewModel.navigationEvent.value)
    }
}

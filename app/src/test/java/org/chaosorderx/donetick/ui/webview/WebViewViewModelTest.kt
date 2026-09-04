package org.chaosorderx.donetick.ui.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
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

        coEvery { getServerConfig.getCurrentConfig() } returns
            ServerConfig(url = "https://example.com", isConfigured = true, lastValidated = 1L)

        vm = WebViewViewModel(getServerConfig, checkConnectivity, notifications, coordinator)
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
        val viewModel = WebViewViewModel(getServerConfig, checkConnectivity, notifications, coordinator)
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
        val viewModel = WebViewViewModel(getServerConfig, checkConnectivity, notifications, coordinator)
        advanceUntilIdle()
        assertNotNull(viewModel.navigationEvent.value)
        viewModel.clearNavigationEvent()
        assertNull(viewModel.navigationEvent.value)
    }
}

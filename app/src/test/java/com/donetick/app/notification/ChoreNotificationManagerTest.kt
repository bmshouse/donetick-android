package com.donetick.app.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.donetick.app.ui.webview.ChoreItem
import com.donetick.app.ui.webview.NotificationMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowNotificationManager
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Use API 28 to avoid notification permission complexity
class ChoreNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var choreNotificationManager: ChoreNotificationManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        choreNotificationManager = ChoreNotificationManager(context)
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)
        
        // Mock NotificationManagerCompat to avoid permission issues
        mockkStatic(NotificationManagerCompat::class)
        val mockNotificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        every { NotificationManagerCompat.from(any()) } returns mockNotificationManagerCompat
        every { mockNotificationManagerCompat.areNotificationsEnabled() } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(NotificationManagerCompat::class)
    }

    @Test
    fun `scheduleChoreNotifications filters and schedules only notification-enabled chores`() {
        // Given
        val futureDate = "2025-12-31T10:00:00Z"
        val chores = listOf(
            ChoreItem(
                id = 1,
                name = "Chore with notification",
                notification = true,
                isActive = true,
                nextDueDate = futureDate
            ),
            ChoreItem(
                id = 2,
                name = "Chore without notification",
                notification = false,
                isActive = true,
                nextDueDate = futureDate
            ),
            ChoreItem(
                id = 3,
                name = "Inactive chore",
                notification = true,
                isActive = false,
                nextDueDate = futureDate
            ),
            ChoreItem(
                id = 4,
                name = "Chore without due date",
                notification = true,
                isActive = true,
                nextDueDate = null
            )
        )

        // When
        choreNotificationManager.scheduleChoreNotifications(chores)

        // Then
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())
        assertTrue(choreNotificationManager.getScheduledNotificationIds().contains(1))
        assertFalse(choreNotificationManager.getScheduledNotificationIds().contains(2))
        assertFalse(choreNotificationManager.getScheduledNotificationIds().contains(3))
        assertFalse(choreNotificationManager.getScheduledNotificationIds().contains(4))
        
        // Verify alarm was scheduled
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `scheduleChoreNotifications cancels existing notifications before scheduling new ones`() {
        // Given - schedule initial notifications
        val initialChores = listOf(
            ChoreItem(
                id = 1,
                name = "Initial chore",
                notification = true,
                isActive = true,
                nextDueDate = "2025-12-31T10:00:00Z"
            )
        )
        choreNotificationManager.scheduleChoreNotifications(initialChores)
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())

        // When - schedule new notifications
        val newChores = listOf(
            ChoreItem(
                id = 2,
                name = "New chore",
                notification = true,
                isActive = true,
                nextDueDate = "2025-12-31T11:00:00Z"
            )
        )
        choreNotificationManager.scheduleChoreNotifications(newChores)

        // Then - old notifications should be cancelled, new ones scheduled
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())
        assertFalse(choreNotificationManager.getScheduledNotificationIds().contains(1))
        assertTrue(choreNotificationManager.getScheduledNotificationIds().contains(2))
    }

    @Test
    fun `scheduleChoreNotifications shows immediate notification for overdue chores`() {
        // Given - chore with past due date
        val pastDate = "2020-01-01T10:00:00Z"
        val chores = listOf(
            ChoreItem(
                id = 1,
                name = "Overdue chore",
                description = "This is overdue",
                notification = true,
                isActive = true,
                nextDueDate = pastDate
            )
        )

        // When
        choreNotificationManager.scheduleChoreNotifications(chores)

        // Then
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())
        assertTrue(choreNotificationManager.getScheduledNotificationIds().contains(1))

        // No alarm should be scheduled for immediate notifications
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)

        // For immediate notifications, we can't easily verify the notification was shown
        // in unit tests without more complex mocking. The important thing is that the
        // notification is tracked and no alarm is scheduled.
    }

    @Test
    fun `cancelChoreNotification removes specific chore from scheduled notifications`() {
        // Given - multiple scheduled notifications
        val futureDate = "2025-12-31T10:00:00Z"
        val chores = listOf(
            ChoreItem(id = 1, name = "Chore 1", notification = true, isActive = true, nextDueDate = futureDate),
            ChoreItem(id = 2, name = "Chore 2", notification = true, isActive = true, nextDueDate = futureDate)
        )
        choreNotificationManager.scheduleChoreNotifications(chores)
        assertEquals(2, choreNotificationManager.getScheduledNotificationCount())

        // When
        choreNotificationManager.cancelChoreNotification(1)

        // Then
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())
        assertFalse(choreNotificationManager.getScheduledNotificationIds().contains(1))
        assertTrue(choreNotificationManager.getScheduledNotificationIds().contains(2))
    }

    @Test
    fun `cancelChoreNotification handles non-existent chore gracefully`() {
        // Given - no scheduled notifications
        assertEquals(0, choreNotificationManager.getScheduledNotificationCount())

        // When - try to cancel non-existent notification
        choreNotificationManager.cancelChoreNotification(999)

        // Then - should not crash and state should remain unchanged
        assertEquals(0, choreNotificationManager.getScheduledNotificationCount())
    }

    @Test
    fun `getScheduledNotificationCount returns correct count`() {
        // Given - no notifications initially
        assertEquals(0, choreNotificationManager.getScheduledNotificationCount())

        // When - schedule some notifications
        val futureDate = "2025-12-31T10:00:00Z"
        val chores = listOf(
            ChoreItem(id = 1, name = "Chore 1", notification = true, isActive = true, nextDueDate = futureDate),
            ChoreItem(id = 2, name = "Chore 2", notification = true, isActive = true, nextDueDate = futureDate)
        )
        choreNotificationManager.scheduleChoreNotifications(chores)

        // Then
        assertEquals(2, choreNotificationManager.getScheduledNotificationCount())
    }

    @Test
    fun `getScheduledNotificationIds returns correct IDs`() {
        // Given
        val futureDate = "2025-12-31T10:00:00Z"
        val chores = listOf(
            ChoreItem(id = 1, name = "Chore 1", notification = true, isActive = true, nextDueDate = futureDate),
            ChoreItem(id = 3, name = "Chore 3", notification = true, isActive = true, nextDueDate = futureDate)
        )

        // When
        choreNotificationManager.scheduleChoreNotifications(chores)

        // Then
        val scheduledIds = choreNotificationManager.getScheduledNotificationIds()
        assertEquals(2, scheduledIds.size)
        assertTrue(scheduledIds.contains(1))
        assertTrue(scheduledIds.contains(3))
        assertFalse(scheduledIds.contains(2))
    }

    @Test
    fun `scheduleChoreNotifications handles empty list`() {
        // Given - empty list
        val emptyChores = emptyList<ChoreItem>()

        // When
        choreNotificationManager.scheduleChoreNotifications(emptyChores)

        // Then
        assertEquals(0, choreNotificationManager.getScheduledNotificationCount())
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `scheduleChoreNotifications handles invalid date format gracefully`() {
        // Given - chore with invalid date
        val chores = listOf(
            ChoreItem(
                id = 1,
                name = "Chore with invalid date",
                notification = true,
                isActive = true,
                nextDueDate = "invalid-date-format"
            )
        )

        // When
        choreNotificationManager.scheduleChoreNotifications(chores)

        // Then - should not crash
        // The implementation parses invalid dates as 0L (epoch), which is treated as an overdue chore
        // So it will be tracked as an immediate notification
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size) // No future alarms scheduled
    }

    @Test
    fun `scheduleChoreNotifications with notification metadata`() {
        // Given - chore with notification metadata
        val futureDate = "2025-12-31T10:00:00Z"
        val chores = listOf(
            ChoreItem(
                id = 1,
                name = "Chore with metadata",
                notification = true,
                isActive = true,
                nextDueDate = futureDate,
                notificationMetadata = NotificationMetadata(dueDate = true)
            )
        )

        // When
        choreNotificationManager.scheduleChoreNotifications(chores)

        // Then
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())
        assertTrue(choreNotificationManager.getScheduledNotificationIds().contains(1))
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `scheduleChoreNotifications prevents duplicate immediate notifications for overdue chores`() {
        // Given - overdue chore that would trigger immediate notification
        val pastDate = "2020-01-01T10:00:00Z"
        val chores = listOf(
            ChoreItem(
                id = 1,
                name = "Overdue chore",
                notification = true,
                isActive = true,
                nextDueDate = pastDate
            )
        )

        // When - schedule notifications twice (simulating app restart or data reload)
        choreNotificationManager.scheduleChoreNotifications(chores)
        val firstNotificationCount = shadowNotificationManager.allNotifications.size

        choreNotificationManager.scheduleChoreNotifications(chores)
        val secondNotificationCount = shadowNotificationManager.allNotifications.size

        // Then - should only have one notification, not duplicates
        assertEquals(1, choreNotificationManager.getScheduledNotificationCount())
        assertTrue(choreNotificationManager.getScheduledNotificationIds().contains(1))
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size) // No future alarms for overdue chores

        // The notification count should be the same after the second call
        // Note: This test assumes the notification system properly handles duplicate IDs
        // In practice, Android's notify() with the same ID replaces the existing notification
        assertEquals(firstNotificationCount, secondNotificationCount)
    }
}

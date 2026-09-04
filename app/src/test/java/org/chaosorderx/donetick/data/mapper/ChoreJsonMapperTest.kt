package org.chaosorderx.donetick.data.mapper

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChoreJsonMapperTest {

    /** Shape taken verbatim from a live `/sync/changes` capture (sanitized). */
    private val liveChore = """
        {
          "id": 23, "name": "Vitamins", "frequencyType": "daily", "frequency": 1,
          "frequencyMetadata": {"unit": null, "time": "", "timezone": "", "weekPattern": null},
          "nextDueDate": "2026-09-03T15:00:00Z", "isRolling": false,
          "assignedTo": 1, "assignees": [{"userId": 1}], "assignStrategy": "least_completed",
          "isActive": true, "notification": true,
          "notificationMetadata": {"circleGroupID": null},
          "labelsV2": [], "circleId": 1, "status": 0, "priority": 0,
          "completionWindow": 3, "description": "", "requireApproval": false,
          "isPrivate": true, "syncVersion": 2440
        }
    """.trimIndent()

    @Test
    fun `maps a live sync chore object`() {
        val c = ChoreJsonMapper.fromJson(JSONObject(liveChore))
        assertEquals(23, c.id)
        assertEquals("Vitamins", c.name)
        assertEquals("2026-09-03T15:00:00Z", c.nextDueDate)
        assertEquals(1, c.assignedTo)
        assertEquals("daily", c.frequencyType)
        assertTrue(c.notification)
        assertTrue(c.isActive)
        assertEquals(0, c.status)
        assertNull("empty description -> null", c.description)
        assertFalse("isCompleted never derived from payload", c.isCompleted)
        assertFalse("notificationMetadata without dueDate key", c.notificationMetadata?.dueDate ?: false)
    }

    @Test
    fun `null and absent fields degrade to sensible defaults`() {
        val c = ChoreJsonMapper.fromJson(
            JSONObject("""{"id": 7, "name": "Go to bed", "nextDueDate": null, "assignedTo": null, "notification": true, "isActive": false}"""),
        )
        assertNull(c.nextDueDate)
        assertNull(c.assignedTo)
        assertFalse(c.isActive)
        assertEquals(1, c.frequency)
        assertEquals(0, c.status)
    }

    @Test
    fun `status is never interpreted as completion`() {
        val c = ChoreJsonMapper.fromJson(JSONObject("""{"id": 1, "name": "x", "status": 1}"""))
        assertEquals(1, c.status)
        assertFalse(c.isCompleted)
    }

    @Test
    fun `honours an explicit isCompleted key (intent round-trip)`() {
        val c = ChoreJsonMapper.fromJson(JSONObject("""{"id": 1, "name": "x", "isCompleted": true}"""))
        assertTrue(c.isCompleted)
    }

    @Test
    fun `fromEnvelope handles res object, bare array, blank, and garbage`() {
        assertEquals(1, ChoreJsonMapper.fromEnvelope("""{"res":[{"id":1,"name":"a"}]}""").size)
        assertEquals(2, ChoreJsonMapper.fromEnvelope("""[{"id":1,"name":"a"},{"id":2,"name":"b"}]""").size)
        assertTrue(ChoreJsonMapper.fromEnvelope("").isEmpty())
        assertTrue(ChoreJsonMapper.fromEnvelope("   ").isEmpty())
        assertTrue(ChoreJsonMapper.fromEnvelope("not json").isEmpty())
    }

    @Test
    fun `notificationMetadata dueDate true is carried`() {
        val json = """{"id":1,"name":"Trash","notificationMetadata":{"dueDate":true,"templates":[{"value":0,"unit":"m"}]}}"""
        val c = ChoreJsonMapper.fromJson(JSONObject(json))
        assertTrue(c.notificationMetadata?.dueDate == true)
    }
}

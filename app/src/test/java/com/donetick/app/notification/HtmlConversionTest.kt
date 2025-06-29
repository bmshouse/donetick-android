package com.donetick.app.notification

import android.os.Build
import android.text.Html
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HtmlConversionTest {

    /**
     * Test utility function that mimics the HTML conversion logic from ChoreNotificationManager
     */
    private fun htmlToNotificationText(htmlText: String?): String {
        if (htmlText.isNullOrEmpty()) return ""
        
        try {
            // Use Html.fromHtml to parse HTML and convert to plain text
            val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlText)
            }
            
            // Convert to string and clean up extra whitespace
            val plainText = spanned.toString()
                .replace(Regex("\\s+"), " ") // Replace multiple whitespace with single space
                .trim()
            
            // Limit length for notification display (Android notifications have character limits)
            return if (plainText.length > 200) {
                plainText.take(197) + "..."
            } else {
                plainText
            }
        } catch (e: Exception) {
            // Fallback: return original text with basic HTML tag removal
            return htmlText.replace(Regex("<[^>]*>"), "").trim()
        }
    }

    @Test
    fun `htmlToNotificationText handles simple HTML tags`() {
        val htmlInput = "<p>Work</p><li>Do</li><li>It</li>"
        val result = htmlToNotificationText(htmlInput)
        
        // Should convert HTML to plain text
        assertEquals("Work Do It", result)
    }

    @Test
    fun `htmlToNotificationText handles complex HTML`() {
        val htmlInput = """
            <div>
                <h2>Weekly Tasks</h2>
                <ul>
                    <li><strong>Clean</strong> the kitchen</li>
                    <li><em>Vacuum</em> the living room</li>
                    <li>Take out <u>trash</u></li>
                </ul>
                <p>Don't forget to <a href="#">check the schedule</a>!</p>
                <br/>
                <span style="color: red;">Important: Complete by Friday</span>
            </div>
        """.trimIndent()
        
        val result = htmlToNotificationText(htmlInput)
        
        // Should convert to readable plain text
        assertTrue("Result should contain 'Weekly Tasks'", result.contains("Weekly Tasks"))
        assertTrue("Result should contain 'Clean the kitchen'", result.contains("Clean the kitchen"))
        assertTrue("Result should contain 'Vacuum the living room'", result.contains("Vacuum the living room"))
        assertTrue("Result should contain 'Take out trash'", result.contains("Take out trash"))
        assertTrue("Result should contain 'check the schedule'", result.contains("check the schedule"))
        assertTrue("Result should contain 'Complete by Friday'", result.contains("Complete by Friday"))
        
        // Should not contain HTML tags
        assertFalse("Result should not contain HTML tags", result.contains("<"))
        assertFalse("Result should not contain HTML tags", result.contains(">"))
    }

    @Test
    fun `htmlToNotificationText handles empty and null input`() {
        assertEquals("", htmlToNotificationText(null))
        assertEquals("", htmlToNotificationText(""))
        assertEquals("", htmlToNotificationText("   "))
    }

    @Test
    fun `htmlToNotificationText handles plain text`() {
        val plainText = "This is just plain text"
        val result = htmlToNotificationText(plainText)
        assertEquals(plainText, result)
    }

    @Test
    fun `htmlToNotificationText handles line breaks and formatting`() {
        val htmlInput = "<p>First paragraph</p><br><p>Second paragraph</p>"
        val result = htmlToNotificationText(htmlInput)
        
        // Should convert to readable text with proper spacing
        assertEquals("First paragraph Second paragraph", result)
    }

    @Test
    fun `htmlToNotificationText limits length for notifications`() {
        val longHtmlInput = "<p>" + "Very long text ".repeat(50) + "</p>"
        val result = htmlToNotificationText(longHtmlInput)
        
        // Should be limited to 200 characters with ellipsis
        assertTrue("Result should be limited to 200 characters", result.length <= 200)
        assertTrue("Result should end with ellipsis", result.endsWith("..."))
    }

    @Test
    fun `htmlToNotificationText handles malformed HTML gracefully`() {
        val malformedHtml = "<p>Unclosed paragraph<div>Mixed tags</p></div>"
        val result = htmlToNotificationText(malformedHtml)
        
        // Should still produce readable text
        assertTrue("Result should contain text content", result.contains("Unclosed paragraph"))
        assertTrue("Result should contain text content", result.contains("Mixed tags"))
        assertFalse("Result should not contain HTML tags", result.contains("<"))
    }

    @Test
    fun `htmlToNotificationText handles special characters`() {
        val htmlInput = "<p>Special chars: &amp; &lt; &gt; &quot; &#39;</p>"
        val result = htmlToNotificationText(htmlInput)
        
        // Should properly decode HTML entities
        assertTrue("Result should contain decoded entities", result.contains("&"))
        assertTrue("Result should contain decoded entities", result.contains("<"))
        assertTrue("Result should contain decoded entities", result.contains(">"))
    }

    @Test
    fun `htmlToNotificationText handles nested tags`() {
        val htmlInput = "<div><p><strong><em>Nested formatting</em></strong></p></div>"
        val result = htmlToNotificationText(htmlInput)
        
        assertEquals("Nested formatting", result)
    }

    @Test
    fun `htmlToNotificationText handles lists properly`() {
        val htmlInput = """
            <ul>
                <li>Item 1</li>
                <li>Item 2</li>
                <li>Item 3</li>
            </ul>
        """.trimIndent()
        
        val result = htmlToNotificationText(htmlInput)
        
        // Should contain all list items
        assertTrue("Result should contain all items", result.contains("Item 1"))
        assertTrue("Result should contain all items", result.contains("Item 2"))
        assertTrue("Result should contain all items", result.contains("Item 3"))
    }
}

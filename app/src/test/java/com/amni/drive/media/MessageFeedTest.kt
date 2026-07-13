package com.amni.drive.media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
class MessageFeedTest {
    private fun msg(sender: String, text: String, at: Long) = IncomingMsg("com.google.android.apps.messaging", "Messages", sender, text, at)
    @Test fun postPublishesLatest() {
        MessageFeed.post(msg("Mom", "dinner?", 1L), "k1|1")
        assertEquals("Mom", MessageFeed.latest.value?.sender)
        MessageFeed.dismiss()
    }
    @Test fun duplicateKeyIgnored() {
        MessageFeed.post(msg("Mom", "dinner?", 2L), "k2|2")
        MessageFeed.dismiss()
        MessageFeed.post(msg("Mom", "dinner?", 2L), "k2|2")
        assertNull(MessageFeed.latest.value)
    }
    @Test fun newKeyReplaces() {
        MessageFeed.post(msg("Mom", "dinner?", 3L), "k3|3")
        MessageFeed.post(msg("Bro", "yo", 4L), "k4|4")
        assertEquals("Bro", MessageFeed.latest.value?.sender)
        MessageFeed.dismiss()
    }
    @Test fun dismissClears() {
        MessageFeed.post(msg("Mom", "x", 5L), "k5|5")
        MessageFeed.dismiss()
        assertNull(MessageFeed.latest.value)
    }
}

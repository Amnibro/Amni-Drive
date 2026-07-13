package com.amni.drive.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class TurnGuidanceTest {
    private fun route(vararg steps: RouteStep) = RouteResult(listOf(RoutePoint(30.0, -97.0), RoutePoint(30.01, -97.0)), listOf(15.0), 1000.0, 120.0, steps.toList())
    @Test fun phraseTurn() {
        assertEquals("Turn right onto Main St", TurnGuidance.phrase("turn", "right", "Main St"))
        assertEquals("Turn left", TurnGuidance.phrase("turn", "left", ""))
        assertEquals("You have arrived", TurnGuidance.phrase("arrive", "", ""))
        assertEquals("Merge left onto I-35", TurnGuidance.phrase("merge", "left", "I-35"))
        assertEquals("Take the roundabout onto FM 620", TurnGuidance.phrase("roundabout", "right", "FM 620"))
        assertEquals("Make a U-turn", TurnGuidance.phrase("turn", "uturn", "Elm"))
        assertEquals("Continue straight onto Oak Ave", TurnGuidance.phrase("continue", "straight", "Oak Ave"))
    }
    @Test fun arrows() {
        assertEquals("←", TurnGuidance.arrowFor("turn", "left"))
        assertEquals("→", TurnGuidance.arrowFor("turn", "right"))
        assertEquals("↖", TurnGuidance.arrowFor("turn", "slight left"))
        assertEquals("⚑", TurnGuidance.arrowFor("arrive", ""))
        assertEquals("↻", TurnGuidance.arrowFor("roundabout", "right"))
        assertEquals("↑", TurnGuidance.arrowFor("continue", "straight"))
    }
    @Test fun distanceFormats() {
        assertEquals("100 ft", TurnGuidance.distText(30.0))
        assertEquals("50 ft", TurnGuidance.distText(3.0))
        assertTrue(TurnGuidance.distText(3218.0).endsWith("mi"))
        assertEquals("In a quarter mile", TurnGuidance.distSpoken(400.0))
        assertEquals("In one mile", TurnGuidance.distSpoken(2500.0))
    }
    @Test fun cueSkipsDepartAndTracksNext() {
        val r = route(
            RouteStep("depart", "", "Start Rd", 30.0, -97.0, 500.0),
            RouteStep("turn", "right", "Main St", 30.005, -97.0, 800.0),
            RouteStep("arrive", "", "", 30.01, -97.0, 0.0)
        )
        val c = TurnGuidance.cue(r, 30.0, -97.0, 0)!!
        assertEquals(1, c.idx)
        assertEquals("→", c.arrow)
        assertTrue(c.text.contains("Turn right onto Main St"))
    }
    @Test fun cueAdvancesWhenManeuverReached() {
        val r = route(
            RouteStep("turn", "right", "Main St", 30.005, -97.0, 800.0),
            RouteStep("arrive", "", "", 30.01, -97.0, 0.0)
        )
        val c = TurnGuidance.cue(r, 30.005, -97.0, 0)!!
        assertEquals(1, c.idx)
        assertEquals("You have arrived", TurnGuidance.phrase(c.step.type, c.step.modifier, c.step.name))
    }
    @Test fun cueAdvancesWhenNextCloserThanCurrent() {
        val r = route(
            RouteStep("turn", "right", "Main St", 30.0, -97.0, 800.0),
            RouteStep("turn", "left", "Elm St", 30.004, -97.0, 300.0),
            RouteStep("arrive", "", "", 30.01, -97.0, 0.0)
        )
        val c = TurnGuidance.cue(r, 30.0035, -97.0, 0)!!
        assertEquals(1, c.idx)
    }
    @Test fun emptyStepsGivesNull() {
        assertNull(TurnGuidance.cue(route(), 30.0, -97.0, 0))
    }
    @Test fun stages() {
        assertEquals(0, TurnGuidance.stageFor(500.0))
        assertEquals(1, TurnGuidance.stageFor(200.0))
        assertEquals(2, TurnGuidance.stageFor(50.0))
    }
}

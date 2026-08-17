package nl.glcillustrious.hk36ttc.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity

/**
 * Regression coverage for a real bug: selecting a runway used to resolve by its free-text
 * display designator, so a grass "02" and an asphalt "02" (two different
 * [RunwayStripEntity] rows, entirely normal at a real field) collided and silently picked
 * whichever one happened to come first — including its surface, so changing the grass
 * condition on the *other* one appeared to do nothing. [RunwayDirectionOption.id] must always
 * be unique regardless of what the pilot typed as a designator.
 */
class RunwayDirectionsTest {

    private fun strip(id: Long, designatorA: String = "02", designatorB: String = "20", oneWay: Boolean = false) =
        RunwayStripEntity(
            id = id,
            airfieldId = 1,
            designatorA = designatorA,
            designatorB = designatorB,
            headingDegTrueA = 20.0,
            lengthM = 800.0,
            surface = "GRASS",
            slopePctA = 0.0,
            oneWay = oneWay
        )

    @Test
    fun `two strips sharing the same displayed designator get distinct ids`() {
        val grass = strip(id = 1, designatorA = "02", designatorB = "20")
        val asphalt = strip(id = 2, designatorA = "02", designatorB = "20")

        val grassIds = grass.directionOptions().map { it.id }
        val asphaltIds = asphalt.directionOptions().map { it.id }

        assertNotEquals(grassIds, asphaltIds)
        assertEquals(0, grassIds.intersect(asphaltIds.toSet()).size)
    }

    @Test
    fun `designator stays as the display label while id is the stable key`() {
        val strip = strip(id = 42, designatorA = "02 gras", designatorB = "20 gras")

        val options = strip.directionOptions()

        assertEquals("42A", options[0].id)
        assertEquals("02 gras", options[0].designator)
        assertEquals("42B", options[1].id)
        assertEquals("20 gras", options[1].designator)
    }

    @Test
    fun `toCandidate maps id to the core key and designator to the label`() {
        val option = strip(id = 7).directionOptions().first()

        val candidate = option.toCandidate()

        assertEquals("7A", candidate.designator)
        assertEquals("02", candidate.label)
    }

    @Test
    fun `a one-way strip only produces direction A`() {
        val options = strip(id = 1, oneWay = true).directionOptions()

        assertEquals(1, options.size)
        assertEquals("1A", options.first().id)
    }

    @Test
    fun `a two-way strip produces both directions with opposite heading and slope`() {
        val options = strip(id = 1, oneWay = false)
            .copy(headingDegTrueA = 30.0, slopePctA = 2.0)
            .directionOptions()

        assertEquals(2, options.size)
        assertEquals(30.0, options[0].headingDegTrue)
        assertEquals(2.0, options[0].slopePct)
        assertEquals(210.0, options[1].headingDegTrue)
        assertEquals(-2.0, options[1].slopePct)
    }
}

/**
 * `deriveOppositeDesignator`/`padDesignatorNumber` back the runway edit dialog's "type A, get B
 * for free" friendliness — a pilot shouldn't have to work out that runway 09's other end is 27.
 */
class DeriveOppositeDesignatorTest {

    @Test
    fun `plain two-digit numbers derive their 18-apart opposite`() {
        assertEquals("20", deriveOppositeDesignator("02"))
        assertEquals("27", deriveOppositeDesignator("09"))
        assertEquals("09", deriveOppositeDesignator("27"))
    }

    /** Works from either end — the pilot isn't forced to always enter the lower number first. */
    @Test
    fun `deriving is symmetric regardless of which end was typed first`() {
        assertEquals(deriveOppositeDesignator("09"), "27")
        assertEquals(deriveOppositeDesignator(deriveOppositeDesignator("09")!!), "09")
    }

    @Test
    fun `the 18 and 36 boundary wraps correctly`() {
        assertEquals("36", deriveOppositeDesignator("18"))
        assertEquals("18", deriveOppositeDesignator("36"))
    }

    @Test
    fun `an unpadded single digit is still recognised as a valid runway number`() {
        assertEquals("20", deriveOppositeDesignator("2"))
    }

    @Test
    fun `a side suffix is swapped, left for right, centre stays centre`() {
        assertEquals("22R", deriveOppositeDesignator("04L"))
        assertEquals("04L", deriveOppositeDesignator("22R"))
        assertEquals("22C", deriveOppositeDesignator("04C"))
    }

    /** The naming-hint example from the runway dialog — trailing disambiguation text survives. */
    @Test
    fun `trailing free text used to disambiguate same-numbered strips is preserved`() {
        assertEquals("20 gras", deriveOppositeDesignator("02 gras"))
        assertEquals("22R beton", deriveOppositeDesignator("04L beton"))
    }

    @Test
    fun `non-numeric or out-of-range designators can't be derived from`() {
        assertNull(deriveOppositeDesignator("H1"))
        assertNull(deriveOppositeDesignator(""))
        assertNull(deriveOppositeDesignator("37"))
        assertNull(deriveOppositeDesignator("00"))
        assertNull(deriveOppositeDesignator("N"))
    }
}

class PadDesignatorNumberTest {

    @Test
    fun `a single digit gets a leading zero`() {
        assertEquals("02", padDesignatorNumber("2"))
    }

    @Test
    fun `an already two-digit number is left alone`() {
        assertEquals("18", padDesignatorNumber("18"))
    }

    @Test
    fun `trailing text and suffixes survive padding untouched`() {
        assertEquals("02 gras", padDesignatorNumber("2 gras"))
        assertEquals("04L", padDesignatorNumber("4L"))
    }

    @Test
    fun `non-numeric designators pass through unchanged`() {
        assertEquals("H1", padDesignatorNumber("H1"))
        assertEquals("", padDesignatorNumber(""))
    }
}

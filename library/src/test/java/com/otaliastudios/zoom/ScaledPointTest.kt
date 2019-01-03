package com.otaliastudios.zoom

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScaledPointTest {

    @Test
    fun `addition of points`() {
        var p = ScaledPoint()
        assert(p.x == 0F)
        assert(p.y == 0F)

        p += ScaledPoint(1F, 1F)

        assert(p.x == 1F)
        assert(p.y == 1F)

        p += ScaledPoint(-1F, -1F)

        assert(p.x == 0F)
        assert(p.y == 0F)
    }

    @Test
    fun `subtraction of points`() {
        var p = ScaledPoint()
        assert(p.x == 0F)
        assert(p.y == 0F)

        p -= ScaledPoint(1F, 1F)

        assert(p.x == -1F)
        assert(p.y == -1F)

        p -= ScaledPoint(-1F, -1F)

        assert(p.x == 0F)
        assert(p.y == 0F)
    }

    @Test
    fun `negation of a point`() {
        var p = ScaledPoint()

        p = -p

        assert(p.x == 0F)
        assert(p.y == 0F)

        p = ScaledPoint(1F, 1F)

        assert(p.x == 1F)
        assert(p.y == 1F)

        p = -p

        assert(p.x == -1F)
        assert(p.y == -1F)
    }

}
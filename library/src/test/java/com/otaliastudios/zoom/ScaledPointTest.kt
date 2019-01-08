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

        assert(p.x == 0F)
        assert(p.y == 0F)

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

    @Test
    fun `multiplication of a point with a factor`() {
        var p = ScaledPoint()

        assert(p.x == 0F)
        assert(p.y == 0F)

        p *= 5

        assert(p.x == 0F)
        assert(p.y == 0F)


        p = ScaledPoint(1F, 1F)
        p = p * 3

        assert(p.x == 3F)
        assert(p.y == 3F)


        p = ScaledPoint(2F, 2F)
        p *= 2

        assert(p.x == 4F)
        assert(p.y == 4F)
    }

}
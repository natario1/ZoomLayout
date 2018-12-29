package com.otaliastudios.zoom

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointTests {

    @Test
    fun `addition of points`() {
        var p = AbsolutePoint(0F, 0F)
        assert(p.x == 0F)
        assert(p.y == 0F)

        p += AbsolutePoint(1F, 1F)

        assert(p.x == 1F)
        assert(p.y == 1F)

        p += AbsolutePoint(-1F, -1F)

        assert(p.x == 0F)
        assert(p.y == 0F)
    }

    @Test
    fun `subtraction of points`() {
        var p = AbsolutePoint(0F, 0F)
        assert(p.x == 0F)
        assert(p.y == 0F)

        p -= AbsolutePoint(1F, 1F)

        assert(p.x == -1F)
        assert(p.y == -1F)

        p -= AbsolutePoint(-1F, -1F)

        assert(p.x == 0F)
        assert(p.y == 0F)
    }

}
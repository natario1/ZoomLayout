package com.otaliastudios.zoom.internal.movement

import com.otaliastudios.zoom.internal.matrix.MatrixController

/**
 * Base class for movement managers like [PanManager] or [ZoomManager].
 *
 * They will typically need access to the [MatrixController] to check the
 * current matrix state.
 */
internal abstract class MovementManager(private val controllerProvider: () -> MatrixController) {

    protected val controller get() = controllerProvider()
}
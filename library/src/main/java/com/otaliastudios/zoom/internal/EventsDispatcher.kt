package com.otaliastudios.zoom.internal

import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomEngine
import com.otaliastudios.zoom.ZoomEngine.Listener

internal class EventsDispatcher(private val engine: ZoomEngine) {

    private val listeners = mutableListOf<ZoomEngine.Listener>()

    internal fun dispatchOnMatrix() {
        listeners.forEach {
            it.onUpdate(engine, engine.matrix)
        }
    }

    internal fun dispatchOnIdle() {
        listeners.forEach {
            it.onIdle(engine)
        }
    }

    /**
     * Registers a new [Listener] to be notified of matrix updates.
     * @param listener the new listener
     *
     */
    internal fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Removes a previously registered listener.
     * @param listener the listener to be removed
     */
    internal fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

}
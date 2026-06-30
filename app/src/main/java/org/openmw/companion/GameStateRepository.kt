package org.openmw.companion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The single source of truth for live game state. The LogReader writes to it;
 * any Compose UI (on either screen) reads from it. Being a plain object means
 * it survives Activity/Service boundaries, which matters when we later move the
 * second-screen rendering into a foreground service.
 */
object GameStateRepository {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun update(transform: (GameState) -> GameState) {
        _state.update(transform)
    }

    /** Called from JNI on the engine thread for every COMPANION_* log line. */
    fun onRawLine(line: String) {
        val trimmed = line.trimEnd()
        _state.update { cur -> LogParser.parseLine(trimmed, cur) ?: cur }
    }
}

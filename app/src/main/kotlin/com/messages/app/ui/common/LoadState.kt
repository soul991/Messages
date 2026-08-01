package com.messages.app.ui.common

/**
 * V2-43. What a list-backed screen actually knows about its data.
 *
 * Home used a nullable list for this, where `null` meant "not loaded yet" — and
 * a nullable list has no room for the fourth thing that can happen, so a query
 * that threw looked exactly like one that had not answered yet: blank, forever.
 */
sealed interface LoadState<out T> {

    /** No answer yet. Says nothing about whether there is any data. */
    data object Loading : LoadState<Nothing>

    data class Ready<out T>(val value: T) : LoadState<T>

    /**
     * The query failed. [reason] is for the log and for a diagnostic line under
     * the retry button — it is a class name or a driver message, not something
     * a person can act on, so it is never the headline.
     */
    data class Failed(val reason: String) : LoadState<Nothing>
}

/** The value if we have one, otherwise null. */
val <T> LoadState<T>.valueOrNull: T? get() = (this as? LoadState.Ready)?.value

/** What to put on screen. */
enum class ListRender {
    /** Deliberately blank: loading, but not for long enough to say so. */
    NOTHING,
    LOADING,
    FAILED,
    EMPTY,
    CONTENT,
}

/**
 * How long a load may take before the UI admits it is loading.
 *
 * Under this, a spinner is worse than a blank: it appears and vanishes inside
 * two frames and reads as a flicker. Over it, a blank is worse than a spinner —
 * that is the finding. Room answers a warm query in well under this, so on the
 * common path nothing is shown at all and the list simply appears.
 */
const val LOADING_GRACE_MILLIS = 150L

/**
 * Maps a load state to what should be rendered, given whether the load has
 * already outlasted [LOADING_GRACE_MILLIS].
 *
 * The empty state is reserved for a *completed* query that returned nothing —
 * which is the whole point: "no conversations yet" is a claim about the data,
 * and making it before the data arrives is a lie the user has no way to check.
 */
fun <T> listRender(state: LoadState<List<T>>, pastGrace: Boolean): ListRender = when {
    state is LoadState.Failed -> ListRender.FAILED
    state is LoadState.Loading && pastGrace -> ListRender.LOADING
    state is LoadState.Loading -> ListRender.NOTHING
    state is LoadState.Ready && state.value.isEmpty() -> ListRender.EMPTY
    else -> ListRender.CONTENT
}

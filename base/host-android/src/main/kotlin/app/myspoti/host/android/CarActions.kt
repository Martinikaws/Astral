package app.myspoti.host.android

/**
 * The side buttons of the player on Android Auto / Automotive (the session's custom actions), in
 * the order the car shows them: the car lays them out in the order they were added and keeps the
 * first ones on small screens, so the most used come first. Pure: the service turns it into
 * [android.support.v4.media.session.PlaybackStateCompat.CustomAction]s.
 */
object CarActions {
    const val LIKE = "like"
    const val SHUFFLE = "shuffle"
    const val REPEAT = "repeat"
    const val RADIO = "radio"
    const val ADD_TO_PLAYLIST = "add_to_playlist"
    const val SPEED = "speed"

    /** Repeat modes of [NowPlayingInfo.repeat]. */
    const val REPEAT_OFF = 0
    const val REPEAT_ALL = 1
    const val REPEAT_ONE = 2

    /** [label] and [icon] are resource ids; the label names what a tap does. */
    data class Spec(val id: String, val label: Int, val icon: Int)

    /** The buttons for [info]: none during an ad or before anything is known, music and episodes differ. */
    fun forState(info: NowPlayingInfo?): List<Spec> {
        if (info == null || info.id == NowPlayingInfo.AD_ID) return emptyList()
        val like = Spec(LIKE, if (info.liked) R.string.msp_unlike else R.string.msp_like,
            if (info.liked) R.drawable.msp_ic_liked else R.drawable.msp_ic_like)
        if (info.episode) return listOf(like, Spec(SPEED, R.string.msp_speed, R.drawable.msp_ic_speed))
        val shuffle = Spec(SHUFFLE, if (info.shuffle) R.string.msp_shuffle_off else R.string.msp_shuffle_on,
            if (info.shuffle) R.drawable.msp_ic_shuffle_on else R.drawable.msp_ic_shuffle)
        val repeat = when (info.repeat) {
            REPEAT_ALL -> Spec(REPEAT, R.string.msp_repeat_one, R.drawable.msp_ic_repeat_on)
            REPEAT_ONE -> Spec(REPEAT, R.string.msp_repeat_off, R.drawable.msp_ic_repeat_one)
            else -> Spec(REPEAT, R.string.msp_repeat_all, R.drawable.msp_ic_repeat)
        }
        return listOf(
            like, shuffle, repeat,
            Spec(RADIO, R.string.msp_start_radio, R.drawable.msp_ic_radio),
            Spec(ADD_TO_PLAYLIST, R.string.msp_add_to_playlist, R.drawable.msp_ic_playlist_add),
        )
    }

    /** The mode a tap on the repeat button goes to: off, all, one, off. */
    fun nextRepeat(mode: Int): Int = when (mode) { REPEAT_OFF -> REPEAT_ALL; REPEAT_ALL -> REPEAT_ONE; else -> REPEAT_OFF }

    /** The speeds the speed button cycles through, for episodes. */
    val SPEEDS = doubleArrayOf(1.0, 1.25, 1.5, 1.75, 2.0)

    /** The speed after [current] (the nearest listed one; the last wraps to 1x). */
    fun nextSpeed(current: Double): Double {
        val at = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01 }
        return if (at >= 0) SPEEDS[(at + 1) % SPEEDS.size] else SPEEDS.firstOrNull { it > current } ?: SPEEDS.first()
    }
}

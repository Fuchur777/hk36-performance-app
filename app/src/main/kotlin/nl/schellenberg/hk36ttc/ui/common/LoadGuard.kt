package nl.schellenberg.hk36ttc.ui.common

/**
 * Guards a calculation screen's persistence write against firing before its saved input has
 * actually been loaded. Every `*ViewModel` in `ui.perf`/`ui.wb` starts its `_state` at the
 * stepper defaults, then asynchronously loads any previously-saved input for the current
 * registration and merges it in. Without this guard, `recalculate()` (called both from that
 * initial load and from every user edit) would happily persist the still-default state if it
 * ever runs before the load completes — silently overwriting a real, previously-saved value
 * with the defaults. This bit us once in production (see docs/00-plan.md, 2026-08-16): the
 * Take-off/Landing/Sleepvlucht ViewModels lacked this guard while WbViewModel accidentally had
 * an equivalent one via its own `profile == null` early-return.
 *
 * Usage: call [markLoaded] once, at the end of the `init` block's load-and-merge, then wrap
 * every persistence write with [runIfLoaded].
 */
class LoadGuard {
    private var loaded = false

    fun markLoaded() {
        loaded = true
    }

    fun runIfLoaded(action: () -> Unit) {
        if (loaded) action()
    }
}

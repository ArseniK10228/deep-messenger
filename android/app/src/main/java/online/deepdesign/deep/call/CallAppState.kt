package online.deepdesign.deep.call

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

object CallAppState {
    fun isInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}

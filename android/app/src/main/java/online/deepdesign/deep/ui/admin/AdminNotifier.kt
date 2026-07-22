package online.deepdesign.deep.ui.admin

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import online.deepdesign.deep.data.UserDto

object AdminNotifier {
    private val _updates = MutableSharedFlow<UserDto>(extraBufferCapacity = 64)
    val updates: SharedFlow<UserDto> = _updates.asSharedFlow()

    fun emit(user: UserDto) {
        _updates.tryEmit(user)
    }
}

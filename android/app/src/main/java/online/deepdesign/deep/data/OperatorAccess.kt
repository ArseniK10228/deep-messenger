package online.deepdesign.deep.data

object OperatorAccess {
    @Volatile
    var canViewPresence: Boolean = false
        private set

    fun update(canView: Boolean?) {
        canViewPresence = canView == true
    }
}

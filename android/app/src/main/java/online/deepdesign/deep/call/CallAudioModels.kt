package online.deepdesign.deep.call

enum class CallOutputRoute(val label: String) {
    Earpiece("Телефон"),
    Speaker("Громкая связь"),
    Bluetooth("Bluetooth"),
    Wired("Наушники")
}

enum class CallInputRoute(val label: String) {
    Phone("Микрофон телефона"),
    Headset("Микрофон наушников")
}

data class CallAudioUiState(
    val outputRoute: CallOutputRoute = CallOutputRoute.Earpiece,
    val inputRoute: CallInputRoute = CallInputRoute.Phone,
    val bluetoothAvailable: Boolean = false,
    val wiredAvailable: Boolean = false,
    val headsetInputAvailable: Boolean = false,
    val bluetoothLabel: String = "Bluetooth",
    val outputLabel: String = CallOutputRoute.Earpiece.label,
    val inputLabel: String = CallInputRoute.Phone.label
) {
    val speakerOn: Boolean get() = outputRoute == CallOutputRoute.Speaker
}

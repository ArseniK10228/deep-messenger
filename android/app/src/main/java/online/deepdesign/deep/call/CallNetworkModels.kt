package online.deepdesign.deep.call

data class CallNetworkUiState(
    val bars: Int = 0,
    val pingMs: Int? = null,
    val statusText: String? = null,
    val reconnecting: Boolean = false
)

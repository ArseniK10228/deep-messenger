package online.deepdesign.deep.data

import android.content.Context
import java.util.UUID

object DeviceIds {
    private const val PREFS = "deep_device"
    private const val KEY = "client_id"

    fun clientId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}

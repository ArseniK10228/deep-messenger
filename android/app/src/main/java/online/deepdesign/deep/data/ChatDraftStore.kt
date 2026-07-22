package online.deepdesign.deep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.chatDraftsDataStore by preferencesDataStore("chat_drafts")

class ChatDraftStore(private val context: Context) {
    private fun draftKey(conversationId: String) = stringPreferencesKey("draft_$conversationId")

    suspend fun getDraft(conversationId: String): String {
        val key = draftKey(conversationId)
        return context.chatDraftsDataStore.data.map { it[key].orEmpty() }.first()
    }

    suspend fun saveDraft(conversationId: String, text: String) {
        val key = draftKey(conversationId)
        context.chatDraftsDataStore.edit { prefs ->
            if (text.isEmpty()) prefs.remove(key)
            else prefs[key] = text
        }
    }
}

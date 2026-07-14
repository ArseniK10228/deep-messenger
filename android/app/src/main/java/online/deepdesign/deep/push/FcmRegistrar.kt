package online.deepdesign.deep.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import online.deepdesign.deep.data.DeepApi
import online.deepdesign.deep.data.FcmRegisterRequest

object FcmRegistrar {
    suspend fun register(api: DeepApi) {
        val token = FirebaseMessaging.getInstance().token.await()
        api.registerFcm(FcmRegisterRequest(token))
    }
}

package online.deepdesign.deep.navigation

object DeepRoutes {
    const val Splash = "splash"
    const val Login = "login"
    const val Chats = "chats"
    const val Chat = "chat/{conversationId}"

    fun chat(conversationId: String) = "chat/$conversationId"
}

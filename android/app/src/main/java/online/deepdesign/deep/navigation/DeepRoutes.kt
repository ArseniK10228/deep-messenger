package online.deepdesign.deep.navigation

object DeepRoutes {
    const val Splash = "splash"
    const val Login = "login"
    const val Chats = "chats"
    const val Admin = "admin"
    const val Chat = "chat/{conversationId}?title={title}"

    fun chat(conversationId: String, title: String) =
        "chat/$conversationId?title=${java.net.URLEncoder.encode(title, "UTF-8")}"
}

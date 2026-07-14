package online.deepdesign.deep.data

import online.deepdesign.deep.BuildConfig

fun resolveMediaUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    return if (path.startsWith("/")) "$base$path" else "$base/$path"
}

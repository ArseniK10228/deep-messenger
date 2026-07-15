package online.deepdesign.deep.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object CallPermissions {
    val audio: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    val video: Array<String> = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    fun hasMic(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCamera(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasVideoCallPermissions(context: Context): Boolean =
        hasMic(context) && hasCamera(context)

    fun missingForVideo(context: Context): Array<String> = buildList {
        if (!hasMic(context)) add(Manifest.permission.RECORD_AUDIO)
        if (!hasCamera(context)) add(Manifest.permission.CAMERA)
    }.toTypedArray()
}

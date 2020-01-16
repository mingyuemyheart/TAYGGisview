package gis.gisdemo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

object PermissionDetect {

    /** Determines if the context calling has the required permission
     * @param context - the IPC context
     * @param permission - The permissions to check
     * @return true if the IPC has the granted permission
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        val res = context.checkCallingOrSelfPermission(permission)
        return res == PackageManager.PERMISSION_GRANTED
    }

    /** Determines if the context calling has the required permissions
     * @param context - the IPC context
     * @param permissions - The permissions to check
     * @return true if the IPC has the granted permission
     */
    fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        var hasAllPermissions = true
        for (permission in permissions) {
            if (!hasPermission(context, permission)) {
                hasAllPermissions = false
            }
        }
        return hasAllPermissions
    }

    fun enforceCallingPermission(context: Context, permission: String): Boolean {
        var result = true
        try {
            context.enforceCallingOrSelfPermission(permission, permission + "permisson denied")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("CaptureAct", "enforceCallingPermission: " + e.message)
            result = false
        }

        Log.d("CaptureAct", "enforceCallingPermission: $permission=$result")
        return result
    }

}

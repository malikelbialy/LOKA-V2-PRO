package com.loka.viva.logic

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import java.util.concurrent.Executors

class AppLauncher(context: Context) {
    private val pm: PackageManager = context.packageManager
    private val executor = Executors.newSingleThreadExecutor()

    interface AppInfo {
        val id: String
        val label: String
        val packageName: String
    }

    fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps: MutableList<AppInfo> = mutableListOf()
        for (info in pm.queryIntentActivities(intent, 0)) {
            val label = info.loadLabel(pm) ?: info.packageName
            apps.add(object : AppInfo {
                override val id = info.packageName
                override val label = label
                override val packageName = info.packageName
            })
        }
        return apps
    }

    fun launchApp(packageName: String) {
        val intent = pm.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun stop() { executor.shutdown() }
}
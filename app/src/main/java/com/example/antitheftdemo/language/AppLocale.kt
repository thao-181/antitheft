package com.example.antitheftdemo.language

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Custom preference storage is retained; AppCompat/native APIs own Activity recreation. */
object AppLocale {
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("setup", Context.MODE_PRIVATE)
        val saved = LanguageCodes.code(prefs.getString("language", null)) ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            if (!prefs.getBoolean("locale_migrated", false)) {
                val manager = context.getSystemService(LocaleManager::class.java)
                if (manager.applicationLocales.isEmpty) manager.applicationLocales = android.os.LocaleList.forLanguageTags(saved)
                prefs.edit().putBoolean("locale_migrated", true).apply()
            }
        } else if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(saved))
        }
    }
    fun apply(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }
    fun current(context: Context): String? {
        val locales = if (Build.VERSION.SDK_INT >= 33)
            LocaleListCompat.wrap(context.getSystemService(LocaleManager::class.java).applicationLocales)
        else AppCompatDelegate.getApplicationLocales()
        return locales[0]?.language?.let(LanguageCodes::code)
    }
    /** Always derive a fresh configuration from application context, including in services. */
    fun context(context: Context): Context {
        val base = context.applicationContext
        val code = current(base) ?: if (Build.VERSION.SDK_INT < 33)
            LanguageCodes.code(base.getSharedPreferences("setup", Context.MODE_PRIVATE).getString("language", null)) else null
        if (code == null) return base
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(code)) })
    }
    fun text(context: Context, reference: String): String {
        val parts = reference.split('|')
        val id = messageResources[parts[0]] ?: return reference
        return context(context).getString(id, *parts.drop(1).toTypedArray())
    }
}

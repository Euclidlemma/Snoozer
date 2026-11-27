package com.example.snoozer.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.example.snoozer.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What Home's Language switch offers: follow the phone, or always English or Korean. */
enum class LanguageChoice(val tag: String?, @StringRes val labelRes: Int) {
    Phone(null, R.string.language_phone),
    English("en", R.string.language_english),
    Korean("ko", R.string.language_korean),
}

/**
 * Snoozer's own language, kept by Android (Android 13+). It's the same setting as Settings →
 * Apps → Snoozer → Language, so the switch and Android's page always agree. Older Android
 * versions have no per-app language; there Snoozer follows the phone and Home hides the switch.
 */
object AppLanguage {
    val canChoose: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun current(context: Context): LanguageChoice {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return LanguageChoice.Phone
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) return LanguageChoice.Phone
        return LanguageChoice.entries.firstOrNull { it.tag == locales[0].language } ?: LanguageChoice.Phone
    }

    /** Android recreates the screen in the new language. */
    fun set(context: Context, choice: LanguageChoice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            choice.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }
}

/**
 * The language the screens are shown in, updated by MainActivity each time it's created (a
 * language change recreates it). ViewModels outlive that, and build their text ahead of time,
 * so they listen here to rebuild it at once instead of at the next minute.
 */
@Singleton
class ShownLanguage @Inject constructor() {
    private val current = MutableStateFlow<Locale?>(null)
    val changes: StateFlow<Locale?> = current

    fun update(locale: Locale) {
        current.value = locale
    }
}

/** This flow, emitting its latest value again whenever the shown language changes. */
fun <T> Flow<T>.alsoOn(language: ShownLanguage): Flow<T> = combine(language.changes) { value, _ -> value }

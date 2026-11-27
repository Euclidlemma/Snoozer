package com.example.snoozer.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.snoozer.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject

/**
 * The app's words, in the person's language, for code that builds sentences outside a
 * composable (the Week, Month and report screens' states, reminders, explanations).
 *
 * The words live in res/values/strings.xml (English) and res/values-ko/strings.xml (Korean).
 * The app reads them through [AndroidStrings]; unit tests read the same files, so a test checks
 * the text people actually see.
 *
 * Build sentences from whole templates with numbered arguments ("%1$s starts in %2$s"), never by
 * joining pieces: Korean puts the parts in a different order.
 */
interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String

    /** The form of [id] for [count] ("1 minute", "5 minutes"); [args] fill its placeholders. */
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String

    /** The language the words are in, for dates and day names. */
    val locale: Locale
}

/**
 * Reads the words from the app's resources. It looks the resources up on every call rather
 * than keeping them, so a language change in Android's settings shows on the next update.
 */
class AndroidStrings @Inject constructor(@ApplicationContext private val context: Context) : Strings {
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.resources.getString(id) else context.resources.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String =
        context.pluralResources().getQuantityString(id, count, *args)

    // The language of the words in use, not the phone's: on a phone set to a language Snoozer
    // isn't translated into, the words fall back to English, and the dates and day names must too.
    override val locale: Locale get() = Locale.forLanguageTag(context.resources.getString(R.string.strings_locale))
}

/** The form of plurals [id] for [count], in a composable ("2 snoozers"). */
@Composable
fun pluralResource(@PluralsRes id: Int, count: Int, vararg args: Any): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration) { context.pluralResources() }
    return resources.getQuantityString(id, count, *args)
}

/**
 * Resources whose plural rules match the words in use. Android picks a plural form by the
 * phone's language, so on a phone set to one Snoozer isn't translated into (Japanese has no
 * singular), the English words would read "1 snoozers". Then this reads them as English.
 */
private fun Context.pluralResources(): Resources {
    val words = Locale.forLanguageTag(resources.getString(R.string.strings_locale))
    if (resources.configuration.locales[0].language == words.language) return resources
    val config = Configuration(resources.configuration).apply { setLocale(words) }
    return createConfigurationContext(config).resources
}

/** [Strings] for a composable, such as a preview that builds a screen state. */
@Composable
fun rememberStrings(): Strings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AndroidStrings(context) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StringsModule {
    @Binds
    abstract fun bindStrings(impl: AndroidStrings): Strings
}

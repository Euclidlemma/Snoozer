package com.example.snoozer.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
        if (args.isEmpty()) context.resources.getQuantityString(id, count)
        else context.resources.getQuantityString(id, count, *args)

    override val locale: Locale get() = context.resources.configuration.locales[0]
}

/** The form of plurals [id] for [count], in a composable ("2 snoozers"). */
@Composable
fun pluralResource(@PluralsRes id: Int, count: Int, vararg args: Any): String {
    LocalConfiguration.current // read again when the language changes
    return LocalContext.current.resources.getQuantityString(id, count, *args)
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

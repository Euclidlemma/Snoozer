package com.example.snoozer.timetable

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.Route.Routes
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.alarm.Category
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/** Why a calendar file couldn't be read, worded for the screen. */
class TimetableReadException(message: String) : Exception(message)

/**
 * Reads the file the user picked (or opened with Snoozer). Through Android's ContentResolver,
 * so it works for Files, Drive, mail attachments and downloads alike; nothing is uploaded.
 */
class TimetableFileReader @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(uri: String): String {
        val stream = try {
            context.contentResolver.openInputStream(uri.toUri())
        } catch (e: Exception) {
            // FileNotFound, a permission that lapsed (after the app was closed), a bad URI.
            Log.w(TAG, "Could not open $uri", e)
            null
        } ?: throw TimetableReadException("Couldn't open that file. Try picking it again.")
        val bytes = stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = try { input.read(buffer) } catch (e: IOException) {
                    throw TimetableReadException("Couldn't read that file. Try picking it again.")
                }
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > IcsParser.MAX_BYTES) throw TimetableReadException("That file is too big to be a timetable.")
            }
            out.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8)
    }

    /**
     * Gives back every lasting read access except [keep]'s: ones left behind when the app was
     * stopped mid-import. Snoozer only ever takes these for imports, so none is needed again.
     */
    fun releaseAllExcept(keep: String?) {
        val held = try { context.contentResolver.persistedUriPermissions } catch (e: Exception) { emptyList() }
        held.map { it.uri.toString() }.filter { it != keep }.forEach(::release)
    }

    /** Gives back the lasting read access the picker took (nothing to give back is fine). */
    fun release(uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Never taken (opened with Snoozer, or the provider didn't offer it).
        }
    }

    private companion object {
        const val TAG = "TimetableFileReader"
    }
}

/** A one-off message for the import screen; [leave] closes it (after the classes were added). */
data class ImportEvent(val message: String?, val leave: Boolean = false)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val reader: TimetableFileReader,
    private val repository: AlarmRepository,
    private val actions: AlarmActions,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val uri: String? = savedStateHandle.get<String>(Routes.ImportTimetable.ARG_URI)?.takeIf { it.isNotBlank() }

    /** What came out of the file, once read. */
    private data class Loaded(
        val candidates: List<TimetableImport.Candidate>,
        val existing: List<Alarm>,
        val skipped: String?,
    )

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val failure = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)

    // The ticks and the Work/Study choice survive the process being killed (as plain values).
    private val checked = MutableStateFlow(savedStateHandle.get<ArrayList<String>>(KEY_CHECKED)?.toSet())
    private val category = MutableStateFlow(
        savedStateHandle.get<String>(KEY_CATEGORY)?.let { l -> Category.entries.firstOrNull { it.label == l } } ?: Category.Study
    )

    private val events = Channel<ImportEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ImportEvent> = events.receiveAsFlow()

    val uiState: StateFlow<ImportUiState> = combine(loaded, failure, checked, category, saving) { l, f, c, cat, s ->
        when {
            f != null -> ImportUiState.Failed(f)
            l == null || c == null -> ImportUiState.Loading
            else -> ImportUiState.Ready(
                rows = TimetableImport.rows(l.candidates, c, l.existing, timeSource.now().toLocalDate()),
                category = cat,
                skipped = l.skipped,
                saving = s,
                limitNote = TimetableImport.budgetNote(l.candidates, c, l.existing, timeSource.now()),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ImportUiState.Loading)

    init {
        reader.releaseAllExcept(uri)
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val file = uri ?: run {
            failure.value = "No file was picked."
            return
        }
        try {
            val text = withContext(Dispatchers.IO) { reader.read(file) }
            if (!IcsParser.looksLikeCalendar(text)) {
                failure.value = "That doesn't look like a calendar (.ics) file."
                return
            }
            val result = withContext(Dispatchers.Default) { IcsParser.parse(text) }
            val existing = repository.getAllAlarmsDirect()
            val candidates = TimetableImport.merge(result.classes)
            if (checked.value == null) {
                setChecked(TimetableImport.defaultChecked(candidates, existing, timeSource.now().toLocalDate()))
            }
            loaded.value = Loaded(candidates, existing, TimetableImport.skippedText(result.skipped))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimetableReadException) {
            failure.value = e.message
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            failure.value = "Couldn't read that file. Is it a calendar (.ics) file?"
        }
    }

    private fun setChecked(keys: Set<String>) {
        checked.value = keys
        savedStateHandle[KEY_CHECKED] = ArrayList(keys)
    }

    fun onToggle(key: String) {
        val current = checked.value ?: return
        setChecked(if (key in current) current - key else current + key)
    }

    fun onCategory(value: Category) {
        category.value = value
        savedStateHandle[KEY_CATEGORY] = value.label
    }

    /** Saves the ticked classes once; a second tap while saving does nothing. */
    fun add() {
        val l = loaded.value ?: return
        val keys = checked.value ?: return
        if (saving.value) return
        val alarms = TimetableImport.toAlarms(l.candidates, keys, l.existing, category.value)
        if (alarms.isEmpty()) return
        if (TimetableImport.budgetNote(l.candidates, keys, l.existing, timeSource.now()) != null) return
        saving.value = true
        viewModelScope.launch {
            val event = try {
                val n = actions.createAll(alarms)
                ImportEvent(if (n == 1) "1 class added" else "$n classes added", leave = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving imported classes failed", e)
                // Some may have been saved before the failure: re-read, so a retry skips those
                // ("Already in Snoozer") instead of adding them twice.
                runCatching { repository.getAllAlarmsDirect() }.getOrNull()?.let { loaded.value = l.copy(existing = it) }
                saving.value = false
                ImportEvent("Couldn't add all the classes. Try again.")
            }
            events.send(event)
        }
    }

    // The preview is closed for good (not just rotated or restarted): the file isn't needed again.
    override fun onCleared() {
        uri?.let(reader::release)
    }

    private companion object {
        const val TAG = "ImportViewModel"
        const val KEY_CHECKED = "import_checked"
        const val KEY_CATEGORY = "import_category"
    }
}

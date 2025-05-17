package com.example.snoozer.dayPickers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.snoozer.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DayPickerViewModel @Inject constructor() : ViewModel() {
    private val _tabIndex: MutableLiveData<Int> = MutableLiveData(0)

    val tabIndex: LiveData<Int> = _tabIndex

    val tabs = listOf(
        R.string.Monday,
        R.string.Tuesday,
        R.string.Wednesday,
        R.string.Thursday,
        R.string.Friday,
        R.string.Saturday,
        R.string.Sunday
    )

    fun updateTabIndexBasedOnSwipe(isSwipeToTheLeft: Boolean) {
        _tabIndex.value = when (isSwipeToTheLeft) {
            true -> Math.floorMod(_tabIndex.value!!.plus(1), tabs.size)
            false -> Math.floorMod(_tabIndex.value!!.minus(1), tabs.size)
        }
    }

    fun updateTabIndex(i: Int) {
        _tabIndex.value = i
    }
}
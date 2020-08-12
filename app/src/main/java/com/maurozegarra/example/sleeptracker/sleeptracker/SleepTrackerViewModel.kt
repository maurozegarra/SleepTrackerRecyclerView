package com.maurozegarra.example.sleeptracker.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.maurozegarra.example.sleeptracker.database.SleepDatabaseDao
import com.maurozegarra.example.sleeptracker.database.SleepNight
import kotlinx.coroutines.*

//@formatter:off
class SleepTrackerViewModel(val database: SleepDatabaseDao,
                            application: Application) : AndroidViewModel(application) {
    //@formatter:on
    // viewModelJob allows us to cancel all coroutines started by this ViewModel.
    private var viewModelJob = Job()

    // A [CoroutineScope] keeps track of all coroutines started by this ViewModel.
    // Because we pass it [viewModelJob], any coroutine started in this uiScope can be cancelled
    // by calling `viewModelJob.cancel()`

    // By default, all coroutines started in uiScope will launch in [Dispatchers.Main] which is
    // the main thread on Android. This is a sensible default because most coroutines started by
    // a [ViewModel] update the UI after performing some processing.

    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var _tonight = MutableLiveData<SleepNight?>()
    val tonight: LiveData<SleepNight?>
        get() = _tonight

    val nights = database.getAllNights()

    // If tonight has not been set, then the START button should be visible.
    val startButtonVisible = Transformations.map(_tonight) {
        it == null
    }

    // If tonight has been set, then the STOP button should be visible.
    val stopButtonVisible = Transformations.map(_tonight) {
        it != null
    }

    // If there are any nights in the database, show the CLEAR button.
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent

    private var _showSnackbarEvent = MutableLiveData<Boolean>()

    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()

    // Call this immediately after navigating to [SleepQualityFragment]
    // It will clear the navigation request, so if the user rotates their phone it won't navigate twice.
    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            _tonight.value = getTonightFromDatabase()
        }
    }

    // Handling the case of the stopped app or forgotten recording, the start and end times will be
    // the same
    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            // If the start time and end time are not the same, then we do not have an unfinished
            // recording.
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }
    }

    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }

    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }

    private suspend fun insert(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(night)
        }
    }

    // Executes when the START button is clicked
    fun onStartTracking() {
        uiScope.launch {
            // Create a new night, which captures the current time,
            // and insert it into the database.
            val newNight = SleepNight()

            insert(newNight)

            _tonight.value = getTonightFromDatabase()
        }
    }


    // Executes when the STOP button is clicked
    fun onStopTracking() {
        uiScope.launch {
            // In Kotlin, the return@label syntax is used for specifying which function among
            // several nested ones this statement returns from.
            // In this case, we are specifying to return from launch(),
            // not the lambda.
            val oldNight = _tonight.value ?: return@launch

            // Update the night in the database to add the end time.
            oldNight.endTimeMilli = System.currentTimeMillis()

            update(oldNight)

            // Set state to navigate to the SleepQualityFragment.
            _navigateToSleepQuality.value = oldNight
        }
    }


    // Executes when the CLEAR button is clicked
    fun onClear() {
        uiScope.launch {
            // Clear the database table.
            clear()

            // And clear tonight since it's no longer in the database
            _tonight.value = null

            _showSnackbarEvent.value = true
        }
    }

    // Called when the ViewModel is dismantled. At this point, we want to cancel all coroutines,
    // otherwise we end up with processes that have nowhere to return to using memory and resources
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val _navigateToSleepDataQuality = MutableLiveData<Long>()
    val navigateToSleepDataQuality
        get() = _navigateToSleepDataQuality

    fun onSleepNightClicked(id: Long) {
        _navigateToSleepDataQuality.value = id
    }

    fun onSleepDataQualityNavigated() {
        _navigateToSleepDataQuality.value = null
    }
}

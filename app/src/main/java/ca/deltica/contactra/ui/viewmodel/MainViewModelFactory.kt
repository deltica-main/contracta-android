package ca.deltica.contactra.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ca.deltica.contactra.data.local.AppDatabase
import ca.deltica.contactra.data.repository.ContactRepositoryImpl

class MainViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val database = AppDatabase.getDatabase(appContext)
            val repository = ContactRepositoryImpl(database)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(appContext, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

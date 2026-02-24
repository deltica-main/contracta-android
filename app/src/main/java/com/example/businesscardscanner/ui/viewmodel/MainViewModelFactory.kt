package com.example.businesscardscanner.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.businesscardscanner.data.local.AppDatabase
import com.example.businesscardscanner.data.repository.ContactRepositoryImpl

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

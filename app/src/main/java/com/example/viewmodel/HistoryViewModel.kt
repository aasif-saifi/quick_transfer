package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.QuickTransferApp
import com.example.data.db.TransferRecordEntity
import com.example.file.FileCategory
import com.example.file.LocalReceivedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuickTransferApp
    private val repository = app.transferRepository
    private val fileManager = app.fileManager

    val transferHistory: StateFlow<List<TransferRecordEntity>> = repository.allTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _receivedFiles = MutableStateFlow<List<LocalReceivedFile>>(emptyList())
    val receivedFiles: StateFlow<List<LocalReceivedFile>> = _receivedFiles.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        refreshReceivedFiles()
    }

    fun refreshReceivedFiles() {
        viewModelScope.launch {
            _receivedFiles.value = fileManager.listReceivedFiles()
        }
    }

    fun selectCategory(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openFile(file: LocalReceivedFile) {
        fileManager.openFile(file)
    }

    fun shareFile(file: LocalReceivedFile) {
        fileManager.shareFile(file)
    }

    fun deleteFile(file: LocalReceivedFile) {
        viewModelScope.launch {
            fileManager.deleteFile(file)
            refreshReceivedFiles()
        }
    }

    fun renameFile(file: LocalReceivedFile, newName: String) {
        viewModelScope.launch {
            fileManager.renameFile(file, newName)
            refreshReceivedFiles()
        }
    }

    fun deleteHistoryRecord(transferId: Long) {
        viewModelScope.launch {
            repository.deleteTransfer(transferId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}

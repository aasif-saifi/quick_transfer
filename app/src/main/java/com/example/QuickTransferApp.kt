package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.TransferRepository
import com.example.file.FileManager
import com.example.network.NsdDiscoveryManager
import com.example.network.SocketTransferEngine
import com.example.network.WifiDirectManager

class QuickTransferApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var transferRepository: TransferRepository
        private set
    lateinit var preferencesRepository: UserPreferencesRepository
        private set
    lateinit var fileManager: FileManager
        private set
    lateinit var nsdDiscoveryManager: NsdDiscoveryManager
        private set
    lateinit var wifiDirectManager: WifiDirectManager
        private set
    lateinit var socketEngine: SocketTransferEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        transferRepository = TransferRepository(database.transferDao())
        preferencesRepository = UserPreferencesRepository(this)
        fileManager = FileManager(this)
        nsdDiscoveryManager = NsdDiscoveryManager(this)
        wifiDirectManager = WifiDirectManager(this)
        socketEngine = SocketTransferEngine(this)
    }
}

package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NsdDiscoveryManager(private val context: Context) {
    private val TAG = "NsdDiscoveryManager"
    private val SERVICE_TYPE = "_quicktransfer._tcp."

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startAdvertising(serviceName: String, port: Int) {
        if (_isAdvertising.value) return
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                Log.d(TAG, "Service registered: ${info?.serviceName}")
                _isAdvertising.value = true
            }

            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
                _isAdvertising.value = false
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                Log.d(TAG, "Service unregistered")
                _isAdvertising.value = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
        }
    }

    fun stopAdvertising() {
        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop advertising", e)
            }
        }
        registrationListener = null
        _isAdvertising.value = false
    }

    fun startDiscovery() {
        if (_isScanning.value) return
        _discoveredDevices.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                Log.d(TAG, "Service discovery started: $regType")
                _isScanning.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service found: ${serviceInfo?.serviceName}")
                if (serviceInfo != null && serviceInfo.serviceType == SERVICE_TYPE) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service lost: ${serviceInfo?.serviceName}")
                val name = serviceInfo?.serviceName ?: return
                _discoveredDevices.value = _discoveredDevices.value.filter { it.name != name }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Discovery stopped: $serviceType")
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                _isScanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
        }
        discoveryListener = null
        _isScanning.value = false
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${info?.serviceName}: $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo?) {
                if (info == null) return
                val host = info.host?.hostAddress ?: return
                val port = info.port
                val deviceName = info.serviceName ?: "Unknown Device"

                // Do not list self if IP matches local exactly on the same port
                val localIp = NetworkUtils.getLocalIpAddress()
                if (host == localIp && port == 8888) {
                    return
                }

                val device = DiscoveredDevice(
                    id = "$host:$port",
                    name = deviceName,
                    ipAddress = host,
                    port = port,
                    transportType = TransportType.WIFI_LOCAL
                )

                val current = _discoveredDevices.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.id == device.id }
                if (existingIndex != -1) {
                    current[existingIndex] = device
                } else {
                    current.add(device)
                }
                _discoveredDevices.value = current
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving service", e)
        }
    }

    fun addManualDevice(device: DiscoveredDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id || it.ipAddress == device.ipAddress }
        if (index != -1) {
            current[index] = device
        } else {
            current.add(device)
        }
        _discoveredDevices.value = current
    }
}

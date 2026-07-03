package com.example.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnStatus {
    STOPPED,
    STARTING,
    ACTIVE,
    PAUSED,
    ERROR
}

object VpnStateTracker {
    private val _status = MutableStateFlow(VpnStatus.STOPPED)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _allowedCount = MutableStateFlow(0)
    val allowedCount: StateFlow<Int> = _allowedCount.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<BlockedLog>>(emptyList())
    val recentLogs: StateFlow<List<BlockedLog>> = _recentLogs.asStateFlow()

    fun setStatus(newStatus: VpnStatus) {
        _status.value = newStatus
    }

    fun incrementBlocked() {
        _blockedCount.value += 1
    }

    fun incrementAllowed() {
        _allowedCount.value += 1
    }

    fun addLog(domain: String, isBlocked: Boolean, category: String) {
        val newLog = BlockedLog(
            timestamp = System.currentTimeMillis(),
            domain = domain,
            isBlocked = isBlocked,
            category = category
        )
        val current = _recentLogs.value.toMutableList()
        current.add(0, newLog)
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _recentLogs.value = current
    }

    fun clearStats() {
        _blockedCount.value = 0
        _allowedCount.value = 0
        _recentLogs.value = emptyList()
    }
}

data class BlockedLog(
    val timestamp: Long,
    val domain: String,
    val isBlocked: Boolean,
    val category: String
)

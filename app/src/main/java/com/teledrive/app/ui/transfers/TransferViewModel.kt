package com.teledrive.app.ui.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.data.db.entity.TransferEntity
import com.teledrive.app.transfer.TransferManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TransferUiState(
    val activeTransfers: List<TransferEntity> = emptyList(),
    val completedTransfers: List<TransferEntity> = emptyList(),
    val failedTransfers: List<TransferEntity> = emptyList()
)

class TransferViewModel(
    private val transferManager: TransferManager = TeleDriveApplication.instance.transferManager
) : ViewModel() {

    val uiState: StateFlow<TransferUiState> = transferManager.getAllTransfers()
        .map { transfers ->
            TransferUiState(
                activeTransfers = transfers.filter { it.status == "PENDING" || it.status == "IN_PROGRESS" },
                completedTransfers = transfers.filter { it.status == "COMPLETED" },
                failedTransfers = transfers.filter { it.status == "FAILED" }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransferUiState()
        )

    fun retryTransfer(id: Long) {
        transferManager.retryTransfer(id)
    }

    fun cancelTransfer(id: Long) {
        transferManager.cancelTransfer(id)
    }

    fun clearCompleted() {
        transferManager.clearCompleted()
    }
}

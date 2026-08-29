package com.teledrive.app.ui.explorer

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SortMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    currentSort: SortBy,
    onSortSelected: (SortBy) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val options = mapOf(
            SortBy.NAME_ASC to "Name (A→Z)",
            SortBy.NAME_DESC to "Name (Z→A)",
            SortBy.DATE_DESC to "Date (Newest)",
            SortBy.DATE_ASC to "Date (Oldest)",
            SortBy.SIZE_DESC to "Size (Largest)",
            SortBy.SIZE_ASC to "Size (Smallest)"
        )
        
        options.forEach { (sort, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onSortSelected(sort)
                    onDismissRequest()
                },
                trailingIcon = {
                    if (currentSort == sort) {
                        Icon(Icons.Default.Check, contentDescription = "Selected")
                    }
                }
            )
        }
    }
}

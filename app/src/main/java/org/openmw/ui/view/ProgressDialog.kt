package org.openmw.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openmw.R

/**
 * Loading Dialog
 * @param isShowCancelableButton show cancel button or not
 * @sample org.openmw.ui.view.ProgressDialogSample
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressDialog(
    modifier: Modifier = Modifier,
    text: String,
    isShowCancelableButton: Boolean = false,
    onCancel: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier,
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Text(text, color = AlertDialogDefaults.titleContentColor)
                Spacer(Modifier.weight(1f))
                if (isShowCancelableButton) {
                    TextButton(onClick = {
                        onCancel.invoke()
                    }) { Text(stringResource(R.string.btn_dismiss)) }
                }
            }
        })
}

@Composable
private fun ProgressDialogSample(modifier: Modifier = Modifier) {
    var progressDialog by rememberSaveable { mutableStateOf(false) }
    if (progressDialog) {
        ProgressDialog(
            text = stringResource(R.string.loading),
            isShowCancelableButton = true,
            onCancel = {
                progressDialog = false
            }
        )
    }
}

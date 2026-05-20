package org.openmw.ui.view

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.openmw.R

val mainMoeScope = MainScope()

val moeDialogQueue = mutableStateListOf<MoeDialogData>()

data class MoeDialogData(
    val text: String,
    val title: String? = null,
    val modifier: Modifier = Modifier,
    val onDismiss: ((MoeDialogData) -> Unit)? = null,
    val onConfirm: ((MoeDialogData) -> Unit)? = null,
    val dismissLabel: String? = null,
    val confirmLabel: String? = null,
    val properties: DialogProperties = DialogProperties(),
    val fullCustom: Boolean = false,
    val content: @Composable ((MoeDialogData) -> Unit)? = null,
)

@Composable
fun MoeDialog() {
    moeDialogQueue.forEach {
        if (it.content == null) {
            AlertDialog(
                modifier = it.modifier,
                onDismissRequest = { it.dismiss() },
                properties = it.properties,
                title = it.title?.let { { Text(it) } },
                text = { Text(text = it.text) },
                confirmButton = {
                    if (it.onConfirm != null && it.confirmLabel != null) {
                        TextButton(onClick = {
                            it.onConfirm.invoke(it)
                            it.dismiss()
                        }) {
                            Text(text = it.confirmLabel)
                        }
                    }
                },
                dismissButton = {
                    if (it.onDismiss != null && it.dismissLabel != null) {
                        TextButton(onClick = {
                            it.onDismiss.invoke(it)
                            it.dismiss()
                        }) {
                            Text(text = it.dismissLabel)
                        }
                    }
                }
            )
        } else if (it.fullCustom) {
            it.content.invoke(it)
        } else {
            AlertDialog(
                modifier = it.modifier,
                onDismissRequest = { it.dismiss() },
                properties = it.properties,
                title = it.title?.let { { Text(it) } },
                text = { it.content.invoke(it) },
                confirmButton = {
                    if (it.onConfirm != null && it.confirmLabel != null) {
                        TextButton(onClick = {
                            it.onConfirm.invoke(it)
                            it.dismiss()
                        }) {
                            Text(text = it.confirmLabel)
                        }
                    }
                },
                dismissButton = {
                    if (it.onDismiss != null && it.dismissLabel != null) {
                        TextButton(onClick = {
                            it.onDismiss.invoke(it)
                            it.dismiss()
                        }) {
                            Text(text = it.dismissLabel)
                        }
                    }
                }
            )
        }
    }
}

fun MoeDialogData.dismiss() = apply {
    mainMoeScope.launch {
        moeDialogQueue.remove(this@dismiss)
    }
}

fun MoeDialogData.show() = apply {
    mainMoeScope.launch {
        moeDialogQueue.add(this@show)
    }
}

fun String.moeDialog(
    context: Context,
    title: String? = null,
    onConfirm: ((MoeDialogData) -> Unit)? = null,
    onDismiss: ((MoeDialogData) -> Unit)? = null,
    confirmLabel: String = context.getString(R.string.btn_confirm),
    dismissLabel: String = context.getString( R.string.btn_cancel),
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    fullCustom: Boolean = false,
    content: @Composable ((MoeDialogData) -> Unit)? = null,
) = MoeDialogData(
    this,
    title,
    modifier,
    onDismiss,
    onConfirm,
    dismissLabel,
    confirmLabel,
    properties,
    fullCustom,
    content = content
).apply { show() }

fun String.moeDialog(
    title: String? = null,
    onConfirm: ((MoeDialogData) -> Unit)? = null,
    onDismiss: ((MoeDialogData) -> Unit)? = null,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    fullCustom: Boolean = false,
    content: @Composable ((MoeDialogData) -> Unit)? = null,
) = MoeDialogData(
    this,
    title,
    modifier,
    onDismiss,
    onConfirm,
    dismissLabel,
    confirmLabel,
    properties,
    fullCustom,
    content = content
).apply { show() }

fun Any?.dialog() = this.toString().moeDialog()
fun Any?.dialog(context: Context) = this.toString().moeDialog(context)

fun String.deleteConfirmDialog(
    onDismiss: ((MoeDialogData) -> Unit)? = null,
    onDelete: ((MoeDialogData) -> Unit)
) = MoeDialogData(
    this,
    null,
    Modifier,
    onDismiss = onDismiss,
    onConfirm = onDelete,
    dismissLabel = null,
    confirmLabel = null,
    fullCustom = true
) {
    AlertDialog(
        modifier = it.modifier,
        onDismissRequest = { it.dismiss() },
        properties = it.properties,
        title = it.title?.let { { Text(it) } },
        text = { Text(text = it.text) },
        confirmButton = {
            if (it.onConfirm != null && it.confirmLabel != null) {
                TextButton(onClick = {
                    it.onConfirm.invoke(it)
                    it.dismiss()
                }) {
                    Text(text = it.confirmLabel)
                }
            }
        },
        dismissButton = {
            if (it.onDismiss != null && it.dismissLabel != null) {
                TextButton(onClick = {
                    it.onDismiss.invoke(it)
                    it.dismiss()
                }) {
                    Text(text = it.dismissLabel)
                }
            }
        }
    )
}.apply { show() }

@file:OptIn(InternalCoroutinesApi::class)

package org.openmw.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.highlightStep
import org.openmw.utils.GameFilesPreferences.setTutorial
import org.openmw.utils.GameFilesPreferences.setWhatsNew

@Composable
fun MyAlertDialog(
    showDialog: MutableState<Boolean>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whatsNew by GameFilesPreferences.getWhatsNew(context).collectAsState(initial = false)
    val tutorial by GameFilesPreferences.getTutorial(context).collectAsState(initial = false)
    val isChecked = whatsNew && tutorial
    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = {
                // Close the dialog when the user touches outside or presses the back button
                //showDialog.value = false
            },
            title = {
                Text(text = stringResource(R.string.welcome_to_alpha_3_launcher))
            },
            text = {
                Column {
                    Text(stringResource(R.string.getting_started))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.close_and_don_t_show_again),
                                color = Color.White
                            )
                        }
                        Switch(
                            checked = !isChecked,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    setWhatsNew(context, false)
                                    setTutorial(context, false)
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            configureControls = true
                            highlightStep = 1

                            scope.launch {
                                setTutorial(context, true)
                            }
                            context.startGame()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black
                        )
                    ) {
                        Text(stringResource(R.string.configure_controls_tutorial), color = Color.White)
                    }
                }
            },
            confirmButton = {
            },
            dismissButton = {
                TextButton (
                    onClick = {
                        showDialog.value = false
                    }
                ) {
                    Text(stringRes(R.string.cancel))
                }
            }
        )
    }
}

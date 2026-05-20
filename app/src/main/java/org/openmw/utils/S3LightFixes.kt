package org.openmw.utils

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.customColor
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@Composable
fun S3LightFixes() {
    val context = LocalContext.current
    var output by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.padding(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        s3LightFixesCMD(context) { commandOutput ->
                            withContext(Dispatchers.Main) {
                                output = output.plus(commandOutput).plus("\n")
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor, // Background color
                    contentColor = Color.White   // Text color
                )
            ) {
                Text(stringResource(R.string.run_s3lightfixes))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = output,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

fun s3LightFixesCMD(context: Context, onOutputUpdate: suspend (String) -> Unit) {
    val overrideFolder = File(Constants.USER_FILE_STORAGE, "OpenMW/Override")
    val cfgFolder = File(Constants.USER_FILE_STORAGE, "config")
    val commands = listOf(
        "./libS3LightFixes.so -o $overrideFolder"
    )
    appendFallbackArchives(context)
    CoroutineScope(Dispatchers.IO).launch {
        // Execute each command sequentially with real-time updates
        for (command in commands) {
            shellExec2(context, command) { outputLine ->
                withContext(Dispatchers.Main) {
                    onOutputUpdate(outputLine)
                }
            }
        }
    }
}

suspend fun shellExec2(context: Context, cmd: String, onOutputUpdate: suspend (String) -> Unit): String {
    val output = StringBuilder()
    val workingDir = context.applicationInfo.nativeLibraryDir
    val overrideFolder = File(Constants.USER_FILE_STORAGE, "OpenMW/Override")
    val cfgFolder = File(Constants.USER_FILE_STORAGE, "config")

    // Ensure there's at least one empty line at the bottom of Constants.USER_OPENMW_CFG
    val userOpenMWCfgFile = File(Constants.USER_OPENMW_CFG)
    if (userOpenMWCfgFile.exists()) {
        val content = userOpenMWCfgFile.readText()
        if (!content.endsWith("\n")) {
            userOpenMWCfgFile.appendText("\n")
        }
    }

    try {
        val processBuilder = ProcessBuilder().apply {
            directory(File(workingDir))
            command("/system/bin/sh", "-c",
                """
            export S3L_DEBUG=1 S3L_NO_NOTIFICATIONS=1;
            export RUST_BACKTRACE=full;            
            export OPENMW_OVERRIDE_DIR=$overrideFolder;
            export OPENMW_CONFIG=$cfgFolder;
            export USER_OPENMW_CFG=${Constants.USER_OPENMW_CFG};
            export OPENMW_CFG=${Constants.OPENMW_CFG};
            $cmd
            """.trimIndent())
            redirectErrorStream(true)
        }

        val process = withContext(Dispatchers.IO) {
            processBuilder.start()
        }
        val reader = process.inputStream.bufferedReader()

        var line: String?
        while (withContext(Dispatchers.IO) {
                reader.readLine()
            }.also { line = it } != null) {
            withContext(Dispatchers.Main) {
                onOutputUpdate(line ?: "")
            }
            output.append(line).append("\n")
        }

        withContext(Dispatchers.IO) {
            process.waitFor()
        }
    } catch (e: Exception) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        val errorOutput = "Error executing command: ${e.message}\nStacktrace:\n$sw"
        withContext(Dispatchers.Main) {
            onOutputUpdate(errorOutput)
        }
        output.append(errorOutput)
    }

    return output.toString()
}

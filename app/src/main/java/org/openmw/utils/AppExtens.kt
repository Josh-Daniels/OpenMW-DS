package org.openmw.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.openmw.EngineActivity
import org.openmw.MyApp.Companion.app

fun Context.Toast(msg: String, isLong: Boolean = false) {
    CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(this@Toast.applicationContext, msg, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

fun MToast(msg: String, isLong: Boolean = false) {
    CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(app, msg, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

fun stringRes(resId: Int) = app.getString(resId)

@OptIn(InternalCoroutinesApi::class)
fun Context.startGame(isFinish: Boolean = true) {
    val intent = Intent(this, EngineActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    this.startActivity(intent)
    if (isFinish) {
        if ((this is Activity)) {
            this.finish()
        }
    }
}
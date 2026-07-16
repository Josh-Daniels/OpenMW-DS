package org.openmw

import android.app.Application
import android.os.Environment
import dagger.hilt.android.HiltAndroidApp
import java.io.File

const val OPENMW_MAIN_LIB = "libopenmw.so"
const val UQM_MAIN_LIB = "libuqm.so"
const val DETHRACE_MAIN_LIB = "libdethrace.so"

// Openmw Path Variables
object Constants {
    const val RANDOM_NUM = "Alpha-22556"

    var USER_FILE_STORAGE = ""
    var SECOND_USER_FILE_STORAGE = ""
    var DEFAULTS_BIN = ""
    var OPENMW_CFG = ""
    var SETTINGS_FILE = ""
    var LOGCAT_FILE = ""
    var OPENMW_LOG = ""
    var GLOBAL_CONFIG = ""
    var USER_CONFIG = ""
    var USER_RESOURCES = ""
    var USER_SAVES = ""
    var USER_DELTA = ""
    var USER_OPENMW_CFG = ""
    var VERSION_STAMP = ""
    var CRASH_FILE = ""
    var INTERNAL_CRASH_FILE = ""
    val CACHE_DIR = Environment.getExternalStorageDirectory().toString() + "/OpenMW-DS/OpenMW/CACHE"
}

@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        app = this

        // Set up global paths
        Constants.USER_FILE_STORAGE = Environment.getExternalStorageDirectory().toString() + "/OpenMW-DS"
        Constants.SECOND_USER_FILE_STORAGE = applicationContext.getExternalFilesDir(null)?.absolutePath ?: ""
        Constants.USER_CONFIG = "${Constants.USER_FILE_STORAGE}/config"
        Constants.USER_RESOURCES = "${Constants.USER_FILE_STORAGE}/resources"
        Constants.USER_SAVES = "${Constants.USER_FILE_STORAGE}/saves"
        Constants.USER_DELTA = "${Constants.USER_FILE_STORAGE}/delta"
        Constants.USER_OPENMW_CFG = "${Constants.USER_CONFIG}/openmw.cfg"
        Constants.SETTINGS_FILE = "${Constants.USER_CONFIG}/settings.cfg"
        Constants.LOGCAT_FILE = "${Constants.USER_CONFIG}/openmw_logcat.txt"
        Constants.OPENMW_LOG = "${Constants.USER_CONFIG}/openmw.log"
        Constants.CRASH_FILE = "${Constants.USER_CONFIG}/crash.log"
        Constants.DEFAULTS_BIN = File(filesDir, "config/defaults.bin").toString()
        Constants.INTERNAL_CRASH_FILE = File(filesDir, "config/crash.log").absolutePath
        Constants.OPENMW_CFG = File(filesDir, "config/openmw.cfg").absolutePath
	    Constants.GLOBAL_CONFIG = File(filesDir, "config").absolutePath
        Constants.VERSION_STAMP = File(filesDir, "stamp").absolutePath
    }

    companion object {
        lateinit var app: MyApp
    }
}

package com.amni.drive.config
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("amni_drive")
class ConfigStore(private val context: Context) {
val config: Flow<DriveConfig> = context.dataStore.data.map {
DriveConfig(
it[MASTER] ?: false,
it[DRIVER] ?: false,
it[REAR] ?: false,
it[THRESH] ?: 1.8f,
it[TIMEOUT] ?: 1800000L,
it[AUTOROUTE] ?: true,
it[AUTOPOWER] ?: true,
it[AUTOBT] ?: false,
it[CARMAC] ?: "",
it[CARMODE] ?: true,
DriveConfig.normalizePath(it[AAPATH]),
it[MESH] ?: true,
it[VOICENAV] ?: true,
it[MSGBANNER] ?: true,
it[MSGREAD] ?: true,
it[HUFULL] ?: true
)
}
suspend fun setMaster(v: Boolean) = write { it[MASTER] = v }
suspend fun setDriver(v: Boolean) = write { it[DRIVER] = v }
suspend fun setRear(v: Boolean) = write { it[REAR] = v }
suspend fun setThreshold(v: Float) = write { it[THRESH] = v }
suspend fun setAutoRoute(v: Boolean) = write { it[AUTOROUTE] = v }
suspend fun setAutoPower(v: Boolean) = write { it[AUTOPOWER] = v }
suspend fun setAutoBt(v: Boolean) = write { it[AUTOBT] = v }
suspend fun setCarMac(v: String) = write { it[CARMAC] = v }
suspend fun setAutoCarMode(v: Boolean) = write { it[CARMODE] = v }
suspend fun setAaPathMode(v: String) = write { it[AAPATH] = DriveConfig.normalizePath(v) }
suspend fun setMeshBridge(v: Boolean) = write { it[MESH] = v }
suspend fun setVoiceNav(v: Boolean) = write { it[VOICENAV] = v }
suspend fun setMsgBanner(v: Boolean) = write { it[MSGBANNER] = v }
suspend fun setMsgAutoRead(v: Boolean) = write { it[MSGREAD] = v }
suspend fun setHuFullscreen(v: Boolean) = write { it[HUFULL] = v }
suspend fun applyGateOffDefaultOnce() = context.dataStore.edit {
if (it[GATE_OFF_V13] != true) {
it[MASTER] = false
it[DRIVER] = false
it[GATE_OFF_V13] = true
}
}
private suspend fun write(block: (MutablePreferences) -> Unit) { context.dataStore.edit(block) }
companion object {
private val MASTER = booleanPreferencesKey("master")
private val DRIVER = booleanPreferencesKey("driver")
private val REAR = booleanPreferencesKey("rear")
private val THRESH = floatPreferencesKey("threshold")
private val TIMEOUT = longPreferencesKey("timeout")
private val AUTOROUTE = booleanPreferencesKey("autoroute")
private val AUTOPOWER = booleanPreferencesKey("autopower")
private val AUTOBT = booleanPreferencesKey("autobt")
private val CARMAC = stringPreferencesKey("carmac")
private val CARMODE = booleanPreferencesKey("carmode")
private val AAPATH = stringPreferencesKey("aapath")
private val MESH = booleanPreferencesKey("meshbridge")
private val VOICENAV = booleanPreferencesKey("voicenav")
private val MSGBANNER = booleanPreferencesKey("msgbanner")
private val MSGREAD = booleanPreferencesKey("msgread")
private val HUFULL = booleanPreferencesKey("hufullscreen")
private val GATE_OFF_V13 = booleanPreferencesKey("gate_off_default_v13")
}
}

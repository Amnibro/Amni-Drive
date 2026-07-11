package com.amni.drive.config
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("amni_drive")
class ConfigStore(private val context: Context) {
    val config: Flow<DriveConfig> = context.dataStore.data.map {
        DriveConfig(it[MASTER] ?: true, it[DRIVER] ?: true, it[REAR] ?: false, it[THRESH] ?: 1.8f, it[TIMEOUT] ?: 1800000L, it[AUTOROUTE] ?: true)
    }
    suspend fun setMaster(v: Boolean) = write { it[MASTER] = v }
    suspend fun setDriver(v: Boolean) = write { it[DRIVER] = v }
    suspend fun setRear(v: Boolean) = write { it[REAR] = v }
    suspend fun setThreshold(v: Float) = write { it[THRESH] = v }
    suspend fun setAutoRoute(v: Boolean) = write { it[AUTOROUTE] = v }
    private suspend fun write(block: (MutablePreferences) -> Unit) { context.dataStore.edit(block) }
    companion object {
        private val MASTER = booleanPreferencesKey("master")
        private val DRIVER = booleanPreferencesKey("driver")
        private val REAR = booleanPreferencesKey("rear")
        private val THRESH = floatPreferencesKey("threshold")
        private val TIMEOUT = longPreferencesKey("timeout")
        private val AUTOROUTE = booleanPreferencesKey("autoroute")
    }
}

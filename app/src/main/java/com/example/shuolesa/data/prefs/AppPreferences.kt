package com.example.shuolesa.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shuolesa_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_SERVER_ENDPOINT = stringPreferencesKey("server_endpoint")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val KEY_AUTO_RESUME_AFTER_CALL = booleanPreferencesKey("auto_resume_after_call")
        private val KEY_TRIGGER_DURATION = androidx.datastore.preferences.core.intPreferencesKey("trigger_duration_sec")
        private val KEY_COZE_BASE_URL = stringPreferencesKey("coze_base_url")
        private val KEY_COZE_API_KEY = stringPreferencesKey("coze_api_key")
        private val KEY_COZE_WORKFLOW_ID = stringPreferencesKey("coze_workflow_id")
    }

    val serverEndpoint: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_ENDPOINT] ?: "" }
    val authToken: Flow<String> = context.dataStore.data.map { it[KEY_AUTH_TOKEN] ?: "" }
    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_HAPTIC_ENABLED] ?: true }
    val autoResumeAfterCall: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_RESUME_AFTER_CALL] ?: false }
    val triggerDuration: Flow<Int> = context.dataStore.data.map { it[KEY_TRIGGER_DURATION] ?: 3 }
    val cozeBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_COZE_BASE_URL] ?: "https://api.coze.cn" }
    val cozeApiKey: Flow<String> = context.dataStore.data.map { it[KEY_COZE_API_KEY] ?: "" }
    val cozeWorkflowId: Flow<String> = context.dataStore.data.map { it[KEY_COZE_WORKFLOW_ID] ?: "" }

    suspend fun setServerEndpoint(value: String) {
        context.dataStore.edit { it[KEY_SERVER_ENDPOINT] = value }
    }

    suspend fun setAuthToken(value: String) {
        context.dataStore.edit { it[KEY_AUTH_TOKEN] = value }
    }

    suspend fun setHapticEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_ENABLED] = value }
    }

    suspend fun setAutoResumeAfterCall(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RESUME_AFTER_CALL] = value }
    }

    suspend fun setTriggerDuration(value: Int) {
        context.dataStore.edit { it[KEY_TRIGGER_DURATION] = value }
    }

    suspend fun setCozeBaseUrl(value: String) {
        context.dataStore.edit { it[KEY_COZE_BASE_URL] = value }
    }

    suspend fun setCozeApiKey(value: String) {
        context.dataStore.edit { it[KEY_COZE_API_KEY] = value }
    }

    suspend fun setCozeWorkflowId(value: String) {
        context.dataStore.edit { it[KEY_COZE_WORKFLOW_ID] = value }
    }

    suspend fun getServerEndpointSync(): String {
        var result = ""
        context.dataStore.edit { result = it[KEY_SERVER_ENDPOINT] ?: "" }
        return result
    }

    suspend fun getAuthTokenSync(): String {
        var result = ""
        context.dataStore.edit { result = it[KEY_AUTH_TOKEN] ?: "" }
        return result
    }

    suspend fun isHapticEnabledSync(): Boolean {
        var result = true
        context.dataStore.edit { result = it[KEY_HAPTIC_ENABLED] ?: true }
        return result
    }

    suspend fun getCozeBaseUrlSync(): String {
        var result = "https://api.coze.cn"
        context.dataStore.edit { result = it[KEY_COZE_BASE_URL] ?: "https://api.coze.cn" }
        return result
    }

    suspend fun getCozeApiKeySync(): String {
        var result = ""
        context.dataStore.edit { result = it[KEY_COZE_API_KEY] ?: "" }
        return result
    }

    suspend fun getCozeWorkflowIdSync(): String {
        var result = ""
        context.dataStore.edit { result = it[KEY_COZE_WORKFLOW_ID] ?: "" }
        return result
    }
}

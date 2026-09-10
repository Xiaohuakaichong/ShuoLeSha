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

enum class ProviderPreset(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultAsrModel: String,
    val defaultLlmModel: String,
) {
    STEPFUN(
        id = "stepfun",
        displayName = "阶跃星辰 (StepFun)",
        defaultBaseUrl = "https://api.stepfun.com/step_plan/v1",
        defaultAsrModel = "stepaudio-2.5-asr",
        defaultLlmModel = "step-router-v1",
    ),
    SILICONFLOW(
        id = "siliconflow",
        displayName = "硅基流动 (SiliconFlow)",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultAsrModel = "FunAudioLLM/SenseVoiceSmall",
        defaultLlmModel = "deepseek-ai/DeepSeek-V3",
    ),
    LOCAL_OR_CUSTOM(
        id = "custom",
        displayName = "本地服务 / 自定义 OpenAI",
        defaultBaseUrl = "http://10.0.2.2:8000/v1",
        defaultAsrModel = "whisper-1",
        defaultLlmModel = "default",
    );

    companion object {
        fun fromId(id: String): ProviderPreset {
            return entries.find { it.id == id } ?: STEPFUN
        }
    }
}

class AppPreferences(private val context: Context) {

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """你是一个随身语音灵感与会议纪要秘书。
请根据用户的录音文字稿，提取并输出结构化的纯 JSON 字符串，格式如下：
{
  "title": "简明凝练的标题（10字以内）",
  "summary": "核心要点提炼（1-3句话，清晰明了）",
  "action_items": ["待办/行动事项1", "待办/行动事项2"],
  "tags": ["分类标签1", "分类标签2"]
}
注意：
1. 仔细分析发言中提及的所有任务、计划、会议、待办事项、约定或行动目标，逐条精炼地放入 action_items 数组；若确实没有任何待办，返回空数组 []；
2. tags 从 [工作, 会议, 灵感, 待办, 学习, 生活, 随想] 中提炼 1-3 个；
3. 必须输出合法且标准的 JSON 对象，不要输出任何推理说明、思考过程或多余文本。"""

        const val DEFAULT_API_KEY = "1UEjWRLNDhItB04RbcyxKZIxxWqWzhVo4O56rkZsrRKBHOZ8FbFkc1bqEKlUWFIxq"

        private val KEY_PROVIDER_MODE = stringPreferencesKey("provider_mode")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_ASR_MODEL = stringPreferencesKey("asr_model")
        private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_KEEP_LOCAL_AUDIO = booleanPreferencesKey("keep_local_audio")

        private val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val KEY_AUTO_RESUME_AFTER_CALL = booleanPreferencesKey("auto_resume_after_call")
        private val KEY_TRIGGER_DURATION = androidx.datastore.preferences.core.intPreferencesKey("trigger_duration_sec")
    }

    val providerMode: Flow<String> = context.dataStore.data.map { it[KEY_PROVIDER_MODE] ?: ProviderPreset.STEPFUN.id }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: ProviderPreset.STEPFUN.defaultBaseUrl }
    val apiKey: Flow<String> = context.dataStore.data.map {
        it[KEY_API_KEY]?.takeIf { k -> k.isNotBlank() } ?: DEFAULT_API_KEY
    }
    val asrModel: Flow<String> = context.dataStore.data.map { it[KEY_ASR_MODEL] ?: ProviderPreset.STEPFUN.defaultAsrModel }
    val llmModel: Flow<String> = context.dataStore.data.map { it[KEY_LLM_MODEL] ?: ProviderPreset.STEPFUN.defaultLlmModel }
    val systemPrompt: Flow<String> = context.dataStore.data.map { it[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT }
    val keepLocalAudio: Flow<Boolean> = context.dataStore.data.map { it[KEY_KEEP_LOCAL_AUDIO] ?: true }

    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_HAPTIC_ENABLED] ?: true }
    val autoResumeAfterCall: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_RESUME_AFTER_CALL] ?: false }
    val triggerDuration: Flow<Int> = context.dataStore.data.map { it[KEY_TRIGGER_DURATION] ?: 3 }

    suspend fun setProviderMode(value: String) {
        context.dataStore.edit { it[KEY_PROVIDER_MODE] = value }
    }

    suspend fun applyPreset(preset: ProviderPreset) {
        context.dataStore.edit {
            it[KEY_PROVIDER_MODE] = preset.id
            it[KEY_BASE_URL] = preset.defaultBaseUrl
            it[KEY_ASR_MODEL] = preset.defaultAsrModel
            it[KEY_LLM_MODEL] = preset.defaultLlmModel
        }
    }

    suspend fun setBaseUrl(value: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = value.trim() }
    }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[KEY_API_KEY] = value.trim() }
    }

    suspend fun setAsrModel(value: String) {
        context.dataStore.edit { it[KEY_ASR_MODEL] = value.trim() }
    }

    suspend fun setLlmModel(value: String) {
        context.dataStore.edit { it[KEY_LLM_MODEL] = value.trim() }
    }

    suspend fun setSystemPrompt(value: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = value }
    }

    suspend fun setKeepLocalAudio(value: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_LOCAL_AUDIO] = value }
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

    // Sync helper methods for background workers
    suspend fun getBaseUrlSync(): String {
        var result = ProviderPreset.STEPFUN.defaultBaseUrl
        context.dataStore.edit { result = it[KEY_BASE_URL] ?: ProviderPreset.STEPFUN.defaultBaseUrl }
        return result
    }

    suspend fun getApiKeySync(): String {
        var result = DEFAULT_API_KEY
        context.dataStore.edit {
            val k = it[KEY_API_KEY]
            result = if (k.isNullOrBlank()) DEFAULT_API_KEY else k
        }
        return result
    }

    suspend fun getAsrModelSync(): String {
        var result = ProviderPreset.STEPFUN.defaultAsrModel
        context.dataStore.edit { result = it[KEY_ASR_MODEL] ?: ProviderPreset.STEPFUN.defaultAsrModel }
        return result
    }

    suspend fun getLlmModelSync(): String {
        var result = ProviderPreset.STEPFUN.defaultLlmModel
        context.dataStore.edit { result = it[KEY_LLM_MODEL] ?: ProviderPreset.STEPFUN.defaultLlmModel }
        return result
    }

    suspend fun getSystemPromptSync(): String {
        var result = DEFAULT_SYSTEM_PROMPT
        context.dataStore.edit { result = it[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT }
        return result
    }

    suspend fun isKeepLocalAudioSync(): Boolean {
        var result = true
        context.dataStore.edit { result = it[KEY_KEEP_LOCAL_AUDIO] ?: true }
        return result
    }

    suspend fun isHapticEnabledSync(): Boolean {
        var result = true
        context.dataStore.edit { result = it[KEY_HAPTIC_ENABLED] ?: true }
        return result
    }
}

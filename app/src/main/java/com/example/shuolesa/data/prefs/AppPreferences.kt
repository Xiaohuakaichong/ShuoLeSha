package com.example.shuolesa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.shuolesa.data.model.stableId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore("shuolesa_prefs")

enum class ProviderPreset(val id: String,val displayName: String,val defaultBaseUrl: String,
    val defaultAsrModel: String,val defaultLlmModel: String) {
    STEPFUN("stepfun","阶跃星辰","https://api.stepfun.com/step_plan/v1","stepaudio-2.5-asr","step-router-v1"),
    SILICONFLOW("siliconflow","硅基流动","https://api.siliconflow.cn/v1","FunAudioLLM/SenseVoiceSmall","deepseek-ai/DeepSeek-V3"),
    LOCAL_OR_CUSTOM("custom","本地 / 自定义","http://10.0.2.2:8000/v1","whisper-1","default");
    companion object { fun fromId(id: String)=entries.find { it.id==id } ?: STEPFUN }
}

data class AiSettings(val provider: String,val baseUrl: String,val apiKey: String,
    val asrModel: String,val llmModel: String,val prompt: String) {
    val local: Boolean get() {
        val url=runCatching { baseUrl.toHttpUrl() }.getOrNull() ?: return false
        val host=url.host
        val parts=host.split('.')
        val bytes=if(parts.size==4) parts.mapNotNull {it.toIntOrNull()?.takeIf {n->n in 0..255}} else emptyList()
        val privateIp=bytes.size==4 && (bytes[0]==10 || bytes[0]==127 || (bytes[0]==192 && bytes[1]==168) || (bytes[0]==172 && bytes[1] in 16..31))
        return url.scheme=="http" && (host=="localhost" || host=="::1" || privateIp || host.endsWith(".local"))
    }
    fun validate() {
        val url=baseUrl.trim().trimEnd('/').toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null) {"服务地址不能包含凭据、查询参数或片段"}
        require(url.scheme=="https" || local) {"云端服务请使用 HTTPS；HTTP 仅用于本地网络"}
        require(asrModel.isNotBlank() && llmModel.isNotBlank()) {"请填写转写与整理模型"}
    }
    override fun toString()="AiSettings(provider=$provider,baseUrl=$baseUrl,credentialConfigured=${apiKey.isNotBlank()})"
}

class AppPreferences(private val context: Context) {
    private val secrets=SecretStore()
    private val store get()=context.dataStore
    companion object {
        const val DEFAULT_SYSTEM_PROMPT="只依据录音原稿整理。区分事实、推测与建议；引用必须来自原文，不推断真人身份。"
        const val DEFAULT_API_KEY=""
        private const val RETIRED_CREDENTIAL_HASH="f5fad720333cfccff25d65695310080f15bc64cf62963e8c99f2f452a35e3dd7"
        private val PROVIDER=stringPreferencesKey("provider_mode")
        private val BASE=stringPreferencesKey("base_url")
        private val OLD_KEY=stringPreferencesKey("api_key")
        private val ASR=stringPreferencesKey("asr_model")
        private val LLM=stringPreferencesKey("llm_model")
        private val PROMPT=stringPreferencesKey("system_prompt")
        private val THEME=stringPreferencesKey("theme_mode")
        private val MODE=stringPreferencesKey("recording_mode")
        private val BITRATE=intPreferencesKey("lifelog_bitrate_kbps")
        private val FORMAT=stringPreferencesKey("meeting_format")
        private val STRATEGY=stringPreferencesKey("launch_strategy")
        private val HAPTIC=booleanPreferencesKey("haptic_enabled")
        private val RESUME=booleanPreferencesKey("auto_resume_after_call")
        private val TRIGGER=intPreferencesKey("trigger_duration_sec")
        private val LIFE_TRIGGER=intPreferencesKey("lifelog_trigger_duration_sec")
        private val MEETING_TRIGGER=intPreferencesKey("meeting_trigger_duration_sec")
        private val KEEP=booleanPreferencesKey("keep_local_audio")
        private val MIGRATED=booleanPreferencesKey("v4_credentials_migrated")
        private fun credential(base: String)=stringPreferencesKey("credential_"+stableId(base.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: base.trimEnd('/')))
    }
    private fun <T> flow(key: Preferences.Key<T>,default: T): Flow<T> = store.data.map { it[key] ?: default }
    private suspend fun <T> set(key: Preferences.Key<T>,value: T) { store.edit { it[key]=value } }
    val providerMode=flow(PROVIDER,"stepfun")
    val baseUrl=flow(BASE,ProviderPreset.STEPFUN.defaultBaseUrl)
    val apiKey=store.data.map { p -> secrets.decrypt(p[credential(p[BASE] ?: ProviderPreset.STEPFUN.defaultBaseUrl)]) }
    val asrModel=flow(ASR,ProviderPreset.STEPFUN.defaultAsrModel)
    val llmModel=flow(LLM,ProviderPreset.STEPFUN.defaultLlmModel)
    val systemPrompt=flow(PROMPT,DEFAULT_SYSTEM_PROMPT)
    val themeMode=flow(THEME,"system")
    val recordingMode=flow(MODE,"lifelog")
    val lifelogBitrateKbps=flow(BITRATE,24)
    val meetingFormat=flow(FORMAT,"wav")
    val launchStrategy=flow(STRATEGY,"default")
    val hapticEnabled=flow(HAPTIC,true)
    val autoResumeAfterCall=flow(RESUME,false)
    val triggerDuration=flow(TRIGGER,3)
    val lifelogTriggerDuration=flow(LIFE_TRIGGER,2)
    val meetingTriggerDuration=flow(MEETING_TRIGGER,4)
    val keepLocalAudio=flow(KEEP,true)

    suspend fun initialize() {
        store.edit { p ->
            if(p[MIGRATED]!=true) {
                val old=p[OLD_KEY].orEmpty()
                val hash=MessageDigest.getInstance("SHA-256").digest(old.toByteArray()).joinToString("") { "%02x".format(it) }
                if(old.isNotBlank() && hash!=RETIRED_CREDENTIAL_HASH) {
                    p[credential(p[BASE] ?: ProviderPreset.STEPFUN.defaultBaseUrl)]=secrets.encrypt(old)
                }
                p.remove(OLD_KEY); p[KEEP]=true; p[MIGRATED]=true
            }
        }
    }
    suspend fun aiSettings(): AiSettings {
        val p=store.data.first(); val base=p[BASE] ?: ProviderPreset.STEPFUN.defaultBaseUrl
        return AiSettings(p[PROVIDER] ?: "stepfun",base,secrets.decrypt(p[credential(base)]),
            p[ASR] ?: ProviderPreset.STEPFUN.defaultAsrModel,p[LLM] ?: ProviderPreset.STEPFUN.defaultLlmModel,
            p[PROMPT] ?: DEFAULT_SYSTEM_PROMPT)
    }
    suspend fun credentialFor(base:String)=secrets.decrypt(store.data.first()[credential(base)])
    fun validate(value: AiSettings) {
        value.validate()
    }
    suspend fun saveAiSettings(value: AiSettings) {
        validate(value)
        val url=value.baseUrl.trim().trimEnd('/').toHttpUrl()
        val encrypted=secrets.encrypt(value.apiKey.trim())
        store.edit { p ->
            p[PROVIDER]=value.provider; p[BASE]=url.toString().trimEnd('/')
            p[ASR]=value.asrModel.trim(); p[LLM]=value.llmModel.trim(); p[PROMPT]=value.prompt
            p[credential(p[BASE]!!)]=encrypted
        }
    }
    suspend fun applyPreset(preset: ProviderPreset) { store.edit {
        it[PROVIDER]=preset.id; it[BASE]=preset.defaultBaseUrl
        it[ASR]=preset.defaultAsrModel; it[LLM]=preset.defaultLlmModel
    } }
    suspend fun setApiKey(value: String) { val p=store.data.first(); set(credential(p[BASE] ?: ProviderPreset.STEPFUN.defaultBaseUrl),secrets.encrypt(value.trim())) }
    suspend fun setBaseUrl(value: String)=set(BASE,value.trim().trimEnd('/'))
    suspend fun setProviderMode(value: String)=set(PROVIDER,value)
    suspend fun setAsrModel(value: String)=set(ASR,value.trim())
    suspend fun setLlmModel(value: String)=set(LLM,value.trim())
    suspend fun setSystemPrompt(value: String)=set(PROMPT,value)
    suspend fun setThemeMode(value: String) { require(value in listOf("light","dark","system")); set(THEME,value) }
    suspend fun setRecordingMode(value: String) { require(value in listOf("lifelog","meeting")); set(MODE,value) }
    suspend fun setLifelogBitrateKbps(value: Int)=set(BITRATE,value.coerceIn(16,32))
    suspend fun setMeetingFormat(value: String)=set(FORMAT,value)
    suspend fun setLaunchStrategy(value: String)=set(STRATEGY,value)
    suspend fun setHapticEnabled(value: Boolean)=set(HAPTIC,value)
    suspend fun setAutoResumeAfterCall(value: Boolean)=set(RESUME,value)
    suspend fun setTriggerDuration(value: Int)=set(TRIGGER,value)
    suspend fun setLifelogTriggerDuration(value: Int)=set(LIFE_TRIGGER,value.coerceIn(1,10))
    suspend fun setMeetingTriggerDuration(value: Int)=set(MEETING_TRIGGER,value.coerceIn(2,15))
    suspend fun setKeepLocalAudio(value: Boolean)=set(KEEP,value)
    suspend fun getBaseUrlSync()=baseUrl.first()
    suspend fun getApiKeySync()=apiKey.first()
    suspend fun getAsrModelSync()=asrModel.first()
    suspend fun getLlmModelSync()=llmModel.first()
    suspend fun getSystemPromptSync()=systemPrompt.first()
    suspend fun isKeepLocalAudioSync()=true
    suspend fun isHapticEnabledSync()=hapticEnabled.first()
    suspend fun getRecordingModeSync()=recordingMode.first()
    suspend fun getLifelogBitrateKbpsSync()=lifelogBitrateKbps.first()
    suspend fun getMeetingFormatSync()=meetingFormat.first()
    suspend fun getLaunchStrategySync()=launchStrategy.first()
    suspend fun getLifelogTriggerDurationSync()=lifelogTriggerDuration.first()
    suspend fun getMeetingTriggerDurationSync()=meetingTriggerDuration.first()
}

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

/**
 * v4.3: [asr] / [llm] = which job the preset can do. 转写 offers the [asr] ones; 整理与提问 offers "same as 转写" or
 * any [llm] one. Keys are stored per preset id, so a company's key (e.g. 百炼) is shared by both jobs, while the two
 * custom entries (转写 gateway vs. 整理 proxy) have separate ids and separate keys.
 */
enum class ProviderPreset(val id: String,val displayName: String,val defaultBaseUrl: String,
    val defaultAsrModel: String,val defaultLlmModel: String,val asr: Boolean=true,val llm: Boolean=true,val note: String?=null) {
    /**
     * v4.2: one 百炼 key covers both transcription (native DashScope ASR, see network/DashScopeAsr) and 整理
     * (OpenAI-compatible chat). qwen-audio-3.1-asr-flash returns sentence times and speaker numbers.
     * Beijing keys and Singapore keys are separate, so the two regions are separate providers.
     * v4.3: 整理 defaults to qwen3.8-flash (about 40% of qwen3.7-plus's price).
     */
    ALIYUN("aliyun","阿里云百炼 · 北京","https://dashscope.aliyuncs.com/compatible-mode/v1","qwen-audio-3.1-asr-flash","qwen3.8-flash"),
    ALIYUN_INTL("aliyun-intl","阿里云百炼 · 新加坡（境外）","https://dashscope-intl.aliyuncs.com/compatible-mode/v1","qwen-audio-3.1-asr-flash","qwen3.8-flash"),
    SILICONFLOW("siliconflow","硅基流动","https://api.siliconflow.cn/v1","FunAudioLLM/SenseVoiceSmall","deepseek-ai/DeepSeek-V3"),
    /** v5.0: an ordinary choice, no longer the default; a fresh install starts with no service selected. */
    STEPFUN("stepfun","阶跃星辰 · Step Plan","https://api.stepfun.com/step_plan/v1","stepaudio-2.5-asr","step-router-v1"),
    LOCAL_OR_CUSTOM("custom","本地 / 自定义","http://10.0.2.2:8000/v1","whisper-1","default",llm=false),
    // v4.3: 整理与提问 only (OpenAI-compatible chat). Thinking is switched off per service in AiClient.chat.
    DEEPSEEK("deepseek","DeepSeek","https://api.deepseek.com","","deepseek-flash",asr=false,
        note="工作日 9–12、14–18 点为高峰价（翻倍）"),
    VOLC_ARK("volcark","火山方舟 · 豆包","https://ark.cn-beijing.volces.com/api/v3","","doubao-seed-2-1-lite-260915",asr=false),
    ZHIPU("zhipu","智谱 GLM","https://open.bigmodel.cn/api/paas/v4","","glm-4.7-flash",asr=false,
        note="glm-4.7-flash 免费，模型较小，建议先试用再作主力"),
    GEMINI("gemini","Google Gemini（境外）","https://generativelanguage.googleapis.com/v1beta/openai","","gemini-3.8-flash",asr=false,
        note="AI Studio 密钥；手机需能访问 Google。免费额度内的内容会被用于改进 Google 产品"),
    CUSTOM_LLM("custom-llm","自定义整理服务（OpenAI 兼容）","http://localhost:8080/v1","","default",asr=false,
        note="电脑上的网关或反代：填 http://localhost:端口/v1 并先 adb reverse tcp:端口 tcp:端口（USB）；"+
            "或用带正式证书的 HTTPS 隧道（如 Tailscale serve）。局域网 IP 的 http 会被拦截。电脑不在线时整理会排队等待，转写不受影响");
    companion object {
        /** v5.0: blank / unknown id = no service chosen yet (there is no default one). */
        fun fromId(id: String)=entries.find { it.id==id }
        fun nameOf(id: String)=fromId(id)?.displayName ?: "所选服务"
        val asrPresets get()=entries.filter { it.asr }
        val llmPresets get()=entries.filter { it.llm }
    }
}

/**
 * v4.2: [hotwords] = names and terms to bias transcription, one per line (services without hotword support ignore it);
 * [diarize] = ask the service to separate speakers when it can.
 * v4.3: [provider] / [baseUrl] / [apiKey] are the 转写 service. 整理与提问 uses the same service unless
 * [llmProvider] and [llmBaseUrl] are set ([split]); then chat calls go there with [llmApiKey] (see [forLlm]).
 * [llmModel] is always the 整理 model, of whichever service does 整理.
 */
data class AiSettings(val provider: String,val baseUrl: String,val apiKey: String,
    val asrModel: String,val llmModel: String,val prompt: String,val hotwords: String="",val diarize: Boolean=true,
    val llmProvider: String="",val llmBaseUrl: String="",val llmApiKey: String="") {
    /** Hotwords as a clean list: split on lines, commas and 、, trimmed, deduplicated, at most [MAX_HOTWORDS]. */
    val hotwordList: List<String> get()=hotwords.split('\n',',','，','、',';','；').map { it.trim() }
        .filter { it.isNotEmpty() && it.length<=MAX_HOTWORD_LENGTH }.distinct().take(MAX_HOTWORDS)
    /**
     * v4.1-a: HTTP is only allowed for hosts whitelisted in res/xml/network_security_config.xml
     * (localhost, 127.0.0.1, 10.0.2.2). Android's NSC cannot whitelist private IP ranges, so a LAN
     * gateway must use HTTPS, or be reached through `adb reverse tcp:8000 tcp:8000` as localhost.
     */
    val local: Boolean get()=isLocal(baseUrl)
    /** v4.3: 整理与提问 has its own service. */
    val split: Boolean get()=llmProvider.isNotBlank() && llmBaseUrl.isNotBlank()
    /** v4.3: the 整理 service is a whitelisted http address (same rule as [local]). */
    val llmLocal: Boolean get()=if(split) isLocal(llmBaseUrl) else local
    /** v4.3: the settings chat calls use: the 整理 service's address and key when [split], otherwise this. */
    fun forLlm(): AiSettings=if(!split) this else
        copy(provider=llmProvider,baseUrl=llmBaseUrl,apiKey=llmApiKey,llmProvider="",llmBaseUrl="",llmApiKey="")
    /** v4.3: every address audio or text goes to, 转写 first. */
    val endpoints: List<String> get()=if(split) listOf(baseUrl,llmBaseUrl) else listOf(baseUrl)
    /** v4.3: what the data-outbound consent covers; one address, or 转写 + 整理 joined by [TARGET_SEPARATOR]. */
    val outboundTarget: String get()=endpoints.joinToString(TARGET_SEPARATOR) { it.trim() }
    /** v4.3: a key (or a local address) for every service in use. */
    val configured: Boolean get()=(apiKey.isNotBlank() || local) && (!split || llmApiKey.isNotBlank() || llmLocal)
    fun validate() {
        require(provider.isNotBlank()) {"请先选择转写服务商"}
        checkAddress(baseUrl,if(split) "转写服务地址" else "服务地址")
        require(llmProvider.isBlank() || llmBaseUrl.isNotBlank()) {"请填写整理服务地址"}
        if(split) checkAddress(llmBaseUrl,"整理服务地址")
        require(asrModel.isNotBlank() && llmModel.isNotBlank()) {"请填写转写与整理模型"}
    }
    companion object {
        val CLEARTEXT_HOSTS=setOf("localhost","127.0.0.1","10.0.2.2")
        const val MAX_HOTWORDS=500
        const val MAX_HOTWORD_LENGTH=30
        /** Spaces can't occur in a valid URL, so the joined consent target splits back unambiguously. */
        const val TARGET_SEPARATOR=" + "
        fun isLocal(base: String): Boolean {
            val url=runCatching { base.trim().toHttpUrl() }.getOrNull() ?: return false
            return url.scheme=="http" && url.host in CLEARTEXT_HOSTS
        }
        private fun checkAddress(base: String,label: String) {
            val url=runCatching { base.trim().trimEnd('/').toHttpUrl() }.getOrNull()
            require(url!=null) {"${label}格式不正确"}
            require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null) {"${label}不能包含凭据、查询参数或片段"}
            require(url.scheme=="https" || isLocal(base)) {"请使用 HTTPS 地址（$label）。HTTP 只允许 localhost、127.0.0.1 和模拟器的 10.0.2.2；局域网网关请配置 HTTPS，或用 adb reverse 映射到 localhost 后再填 http://localhost"}
        }
    }
    override fun toString()="AiSettings(provider=$provider,baseUrl=$baseUrl,credentialConfigured=${apiKey.isNotBlank()}"+
        (if(split) ",llmProvider=$llmProvider,llmBaseUrl=$llmBaseUrl,llmCredentialConfigured=${llmApiKey.isNotBlank()}" else "")+")"
}

/** [testStore]: an isolated store for tests; the app-wide [dataStore] is one per process and keeps state between tests. */
class AppPreferences(private val context: Context,private val testStore: androidx.datastore.core.DataStore<Preferences>?=null) {
    private val secrets=SecretStore()
    private val store get()=testStore ?: context.dataStore
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
        // v4.1-a
        private val PROVIDER_MIGRATED=booleanPreferencesKey("v41_credentials_by_provider")
        private val PRIVACY_CONSENT=booleanPreferencesKey("privacy_consent_v1")
        private val BLIND_HINT_OFF=booleanPreferencesKey(com.example.shuolesa.ui.logic.NoticeKind.BLIND_SYSTEM_LIMIT.prefKey!!)
        private val BLIND_FAILED_PENDING=booleanPreferencesKey("blind_fallback_pending")
        private val HOME_BLIND_CARD_OFF=booleanPreferencesKey(com.example.shuolesa.ui.logic.NoticeKind.HOME_BLIND_FALLBACK.prefKey!!)
        private val PRIVACY_CONSENT_AT=longPreferencesKey("privacy_consent_at")
        private val OUTBOUND_BASE=stringPreferencesKey("outbound_consent_base_url")
        private val ONBOARDED=booleanPreferencesKey("onboarding_done_v41")
        // v4.2
        private val HOTWORDS=stringPreferencesKey("asr_hotwords")
        private val DIARIZE=booleanPreferencesKey("asr_diarize")
        // v4.3: 整理与提问 service when it is not the 转写 one (blank = same service)
        private val LLM_PROVIDER=stringPreferencesKey("llm_provider_mode")
        private val LLM_BASE=stringPreferencesKey("llm_base_url")
        // v5.0: the implicit StepFun default is gone; installs that relied on it get it written down once.
        private val EXPLICIT_PROVIDER=booleanPreferencesKey("v50_explicit_provider")
        // v5.2: show text while recording (network/LiveCaptions)
        private val LIVE_CAPTIONS=booleanPreferencesKey("live_captions")
        private fun providerCredential(provider: String)=stringPreferencesKey("credential_provider_$provider")
        fun normalizeBase(base: String)=base.trim().toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: base.trim().trimEnd('/')
        /** v4.3: the addresses in a consent target ([AiSettings.outboundTarget]), 转写 first. */
        fun targets(target: String)=target.split(AiSettings.TARGET_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        /** v4.3: [normalizeBase] for every address of a consent target; a single address normalises as before. */
        fun normalizeTarget(target: String)=targets(target).joinToString(AiSettings.TARGET_SEPARATOR) { normalizeBase(it) }
        /** v4.3: the target the saved settings send data to (转写 address, plus the 整理 address when split). */
        private fun savedTarget(p: Preferences): String {
            val base=normalizeBase(p[BASE].orEmpty())
            val llmBase=p[LLM_BASE]?.takeIf { !p[LLM_PROVIDER].isNullOrBlank() && it.isNotBlank() } ?: return base
            return base+AiSettings.TARGET_SEPARATOR+normalizeBase(llmBase)
        }
        private fun credential(base: String)=stringPreferencesKey("credential_"+stableId(base.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: base.trimEnd('/')))
    }
    private fun <T> flow(key: Preferences.Key<T>,default: T): Flow<T> = store.data.map { it[key] ?: default }
    private suspend fun <T> set(key: Preferences.Key<T>,value: T) { store.edit { it[key]=value } }
    val providerMode=flow(PROVIDER,"")
    val baseUrl=flow(BASE,"")
    /** v4.1-a: the credential belongs to the provider id, so editing the base URL keeps it. */
    val apiKey=store.data.map { p -> secrets.decrypt(p[providerCredential(p[PROVIDER].orEmpty())]) }
    val privacyConsented=flow(PRIVACY_CONSENT,false)
    /** v4.1 ⑦: "以后不提示" on the blind-trigger fallback notification. */
    val blindHintOff=flow(BLIND_HINT_OFF,false)
    /** v4.1 ⑦: blind trigger failed while notifications were off -> home shows 刚才的快捷录音没有开始. */
    val blindFailedPending=flow(BLIND_FAILED_PENDING,false)
    /** 首页「刚才的快捷录音没有开始」卡片「以后不提示」（与通知的开关分开保存）。 */
    val homeBlindCardOff=flow(HOME_BLIND_CARD_OFF,false)
    suspend fun setHomeBlindCardOff(value:Boolean) { store.edit { it[HOME_BLIND_CARD_OFF]=value } }
    /** 设置 › 外观「恢复所有提示」: clears every「以后不提示」 flag (NoticePolicy.dismissalKeys). */
    suspend fun clearAllHintDismissals() { store.edit { p -> com.example.shuolesa.ui.logic.NoticePolicy.dismissalKeys.forEach { p.remove(booleanPreferencesKey(it)) } } }
    suspend fun setBlindHintOff(value:Boolean) { store.edit { it[BLIND_HINT_OFF]=value } }
    suspend fun setBlindFailedPending(value:Boolean) { store.edit { it[BLIND_FAILED_PENDING]=value } }
    val onboardingDone=flow(ONBOARDED,false)
    /**
     * Base URL the user confirmed on the data-outbound page; null if never confirmed.
     * v4.3: with a separate 整理 service this is "转写地址 + 整理地址" ([AiSettings.outboundTarget]); see [targets].
     */
    val outboundConsentBase=store.data.map { it[OUTBOUND_BASE] }
    /** True only when the confirmed target equals the current one (a changed or added address needs re-confirmation). */
    val outboundConsented=store.data.map { p -> p[PRIVACY_CONSENT]==true && p[OUTBOUND_BASE]!=null &&
        p[OUTBOUND_BASE]==savedTarget(p) }
    val asrModel=flow(ASR,"")
    val llmModel=flow(LLM,"")
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
    val liveCaptions=flow(LIVE_CAPTIONS,true)
    suspend fun setLiveCaptions(value: Boolean)=set(LIVE_CAPTIONS,value)

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
            // v4.1-a: per-baseUrl -> per-provider credentials. Old entries stay (harmless, allows rollback).
            if(p[PROVIDER_MIGRATED]!=true) {
                val current=p[PROVIDER] ?: "stepfun"
                val currentBase=p[BASE] ?: ProviderPreset.STEPFUN.defaultBaseUrl
                p[credential(currentBase)]?.takeIf { it.isNotBlank() }?.let { p[providerCredential(current)]=it }
                ProviderPreset.entries.forEach { preset ->
                    if(p[providerCredential(preset.id)].isNullOrBlank())
                        p[credential(preset.defaultBaseUrl)]?.takeIf { it.isNotBlank() }?.let { p[providerCredential(preset.id)]=it }
                }
                p[PROVIDER_MIGRATED]=true
            }
            // v5.0: before this, a missing provider meant StepFun. Keep that for installs that already use it
            // (a stored StepFun key); everyone else starts with no service selected.
            if(p[EXPLICIT_PROVIDER]!=true) {
                if(p[PROVIDER]==null && !p[providerCredential(ProviderPreset.STEPFUN.id)].isNullOrBlank()) {
                    p[PROVIDER]=ProviderPreset.STEPFUN.id
                    if(p[BASE]==null) p[BASE]=ProviderPreset.STEPFUN.defaultBaseUrl
                    if(p[ASR]==null) p[ASR]=ProviderPreset.STEPFUN.defaultAsrModel
                    if(p[LLM]==null) p[LLM]=ProviderPreset.STEPFUN.defaultLlmModel
                }
                p[EXPLICIT_PROVIDER]=true
            }
        }
    }
    suspend fun setPrivacyConsent(value: Boolean) { store.edit { p ->
        p[PRIVACY_CONSENT]=value
        if(value) p[PRIVACY_CONSENT_AT]=System.currentTimeMillis()
        else { p.remove(PRIVACY_CONSENT_AT); p.remove(OUTBOUND_BASE) } // revoking privacy also revokes outbound
    } }
    /** Confirms sending audio/text to [base] (normally the current [AiSettings.outboundTarget]). */
    suspend fun setOutboundConsent(base: String) { store.edit { it[OUTBOUND_BASE]=normalizeTarget(base) } }
    suspend fun revokeOutboundConsent() { store.edit { it.remove(OUTBOUND_BASE) } }
    suspend fun setOnboardingDone(value: Boolean)=set(ONBOARDED,value)
    suspend fun isOutboundConsented()=outboundConsented.first()
    /**
     * True when [base] (an address or an [AiSettings.outboundTarget]) contains an address the user has not confirmed.
     * v4.3: fewer addresses than confirmed (整理 moved back onto the 转写 service) need no new confirmation.
     */
    suspend fun needsOutboundReconfirm(base: String): Boolean {
        val consented=store.data.first()[OUTBOUND_BASE] ?: return true
        val confirmed=targets(consented).map { normalizeBase(it) }.toSet()
        val wanted=targets(base)
        return wanted.isEmpty() || wanted.any { normalizeBase(it) !in confirmed }
    }
    suspend fun aiSettings(): AiSettings {
        val p=store.data.first(); val base=p[BASE].orEmpty()
        val provider=p[PROVIDER].orEmpty()
        val llmProvider=p[LLM_PROVIDER].orEmpty();val llmBase=p[LLM_BASE].orEmpty()
        val split=llmProvider.isNotBlank() && llmBase.isNotBlank()
        return AiSettings(provider,base,secrets.decrypt(p[providerCredential(provider)]),
            p[ASR].orEmpty(),p[LLM].orEmpty(),
            p[PROMPT] ?: DEFAULT_SYSTEM_PROMPT,p[HOTWORDS].orEmpty(),p[DIARIZE] ?: true,
            if(split) llmProvider else "",if(split) llmBase else "",if(split) secrets.decrypt(p[providerCredential(llmProvider)]) else "")
    }
    /** v4.1-a: credentials are looked up by provider id, not by base URL. */
    suspend fun credentialFor(provider:String)=secrets.decrypt(store.data.first()[providerCredential(provider)])
    fun validate(value: AiSettings) {
        value.validate()
    }
    /**
     * v4.1-a: a blank key never overwrites a stored one unless [clearKey] is true
     * (the explicit "清除密钥" action). Keys are stored per provider id.
     */
    suspend fun saveAiSettings(value: AiSettings,clearKey: Boolean=false) {
        validate(value)
        val url=value.baseUrl.trim().trimEnd('/').toHttpUrl()
        val key=value.apiKey.trim()
        val encrypted=if(key.isNotBlank()) secrets.encrypt(key) else null
        // v4.3: the 整理 service's key is stored under its own provider id (a blank one keeps the stored key).
        val llmKey=value.llmApiKey.trim()
        val llmEncrypted=if(value.split && llmKey.isNotBlank()) secrets.encrypt(llmKey) else null
        store.edit { p ->
            p[PROVIDER]=value.provider; p[BASE]=url.toString().trimEnd('/')
            p[ASR]=value.asrModel.trim(); p[LLM]=value.llmModel.trim(); p[PROMPT]=value.prompt
            p[HOTWORDS]=value.hotwords.take(8000); p[DIARIZE]=value.diarize
            when {
                encrypted!=null -> p[providerCredential(value.provider)]=encrypted
                clearKey -> p.remove(providerCredential(value.provider))
            }
            if(value.split) {
                p[LLM_PROVIDER]=value.llmProvider; p[LLM_BASE]=value.llmBaseUrl.trim().trimEnd('/').toHttpUrl().toString().trimEnd('/')
                llmEncrypted?.let { p[providerCredential(value.llmProvider)]=it }
            } else { p.remove(LLM_PROVIDER); p.remove(LLM_BASE) }
        }
    }
    suspend fun clearApiKey(provider: String) { store.edit { it.remove(providerCredential(provider)) } }
    suspend fun applyPreset(preset: ProviderPreset) { store.edit {
        it[PROVIDER]=preset.id; it[BASE]=preset.defaultBaseUrl
        it[ASR]=preset.defaultAsrModel; it[LLM]=preset.defaultLlmModel
    } }
    suspend fun setApiKey(value: String) { if(value.isBlank()) return; val provider=store.data.first()[PROVIDER]; if(provider.isNullOrBlank()) return; set(providerCredential(provider),secrets.encrypt(value.trim())) }
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

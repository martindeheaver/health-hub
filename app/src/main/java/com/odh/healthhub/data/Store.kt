package com.odh.healthhub.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

data class Analysis(
    val date: LocalDate,
    val text: String,
    val createdAt: Long,
    val model: String,
)

/** Small on-device store for settings and saved AI insights (app-private storage). */
class Store(context: Context) {
    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
        private const val PREFIX = "analysis_"
    }

    private val p = context.getSharedPreferences("healthhub", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) = p.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = p.getString("model", DEFAULT_MODEL)?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
        set(v) = p.edit().putString("model", v.trim()).apply()

    var aboutMe: String
        get() = p.getString("about_me", "") ?: ""
        set(v) = p.edit().putString("about_me", v).apply()

    var heightCm: String
        get() = p.getString("height_cm", "") ?: ""
        set(v) = p.edit().putString("height_cm", v.trim()).apply()

    val heightCmValue: Double? get() = heightCm.toDoubleOrNull()

    var autoAnalyse: Boolean
        get() = p.getBoolean("auto_analyse", true)
        set(v) = p.edit().putBoolean("auto_analyse", v).apply()

    fun analysis(date: LocalDate): Analysis? =
        p.getString(PREFIX + date, null)?.let(::parse)

    fun saveAnalysis(a: Analysis) {
        val json = JSONObject()
            .put("date", a.date.toString())
            .put("text", a.text)
            .put("createdAt", a.createdAt)
            .put("model", a.model)
        p.edit().putString(PREFIX + a.date, json.toString()).apply()
    }

    fun allAnalyses(): List<Analysis> =
        p.all.filterKeys { it.startsWith(PREFIX) }
            .values.mapNotNull { (it as? String)?.let(::parse) }
            .sortedByDescending { it.date }

    private fun parse(s: String): Analysis? = try {
        val j = JSONObject(s)
        Analysis(LocalDate.parse(j.getString("date")), j.getString("text"), j.getLong("createdAt"), j.optString("model"))
    } catch (e: Exception) { null }
}

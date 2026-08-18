package com.example.dreamnet.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ExternalDatabaseImporter(
    private val dreamDao: DreamDao,
    @Suppress("unused") private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val GITHUB_URL = "https://raw.githubusercontent.com/bayerasher/bayerasher.github.io/main/database.json"

    suspend fun sync() = withContext(Dispatchers.IO) {
        try {
            Log.d("ExternalImporter", "Starting sync from GitHub...")
            val request = Request.Builder().url(GITHUB_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ExternalImporter", "Failed to download database: ${response.code}")
                    return@withContext
                }

                val jsonString = response.body?.string() ?: return@withContext
                val jsonArray = JSONArray(jsonString)
                Log.d("ExternalImporter", "Downloaded ${jsonArray.length()} entries.")

                for (i in 0 until jsonArray.length() step 100) {
                    val end = minOf(i + 100, jsonArray.length())
                    importBatch(jsonArray, i, end)
                }
                Log.d("ExternalImporter", "Sync completed successfully.")
            }
        } catch (e: Exception) {
            Log.e("ExternalImporter", "Sync error", e)
        }
    }

    private suspend fun importBatch(jsonArray: JSONArray, start: Int, end: Int) {
        for (i in start until end) {
            val obj = jsonArray.getJSONObject(i)
            val ruTitle = obj.optString("ru_title", "").trim()
            val ruText = obj.optString("ru_text", "").trim()
            val uzText = obj.optString("uz_text", "").trim()
            val enText = obj.optString("en_text", "").trim()

            if (ruTitle.isEmpty()) continue

            val sourceId = extractSourceId(ruTitle)
            val cleanWord = removeSourceFromTitle(ruTitle)
            val linkedEntryGroupId = 200000L + i 

            if (ruText.isNotEmpty() || ruTitle.isNotEmpty()) {
                val entryId = dreamDao.insertEntry(DreamEntry(
                    word = cleanWord,
                    language = "Русский",
                    linkedEntryGroupId = linkedEntryGroupId
                ))
                if (ruText.isNotEmpty()) {
                    dreamDao.insertMeaning(DreamMeaning(
                        dreamEntryId = entryId,
                        sourceId = sourceId ?: "external_github",
                        meaning = ruText,
                        language = "Русский"
                    ))
                }
                dreamDao.insertVariant(DreamVariant(
                    dreamEntryId = entryId,
                    variant = cleanWord,
                    normalizedVariant = cleanWord.lowercase().replace("ё", "е")
                ))
            }

            if (enText.isNotEmpty()) {
                val wordEn = obj.optString("en_title", "").ifEmpty { cleanWord }.trim()
                val entryId = dreamDao.insertEntry(DreamEntry(
                    word = wordEn,
                    language = "English",
                    linkedEntryGroupId = linkedEntryGroupId
                ))
                dreamDao.insertMeaning(DreamMeaning(
                    dreamEntryId = entryId,
                    sourceId = sourceId ?: "external_github",
                    meaning = enText,
                    language = "English"
                ))
                dreamDao.insertVariant(DreamVariant(
                    dreamEntryId = entryId,
                    variant = wordEn,
                    normalizedVariant = wordEn.lowercase()
                ))
            }

            if (uzText.isNotEmpty()) {
                val wordUz = obj.optString("uz_title", "").ifEmpty { cleanWord }.trim()
                val entryId = dreamDao.insertEntry(DreamEntry(
                    word = wordUz,
                    language = "O'zbek",
                    linkedEntryGroupId = linkedEntryGroupId
                ))
                dreamDao.insertMeaning(DreamMeaning(
                    dreamEntryId = entryId,
                    sourceId = sourceId ?: "external_github",
                    meaning = uzText,
                    language = "O'zbek"
                ))
                dreamDao.insertVariant(DreamVariant(
                    dreamEntryId = entryId,
                    variant = wordUz,
                    normalizedVariant = wordUz.lowercase()
                ))
            }
        }
    }

    private fun extractSourceId(title: String): String? {
        val matcher = Pattern.compile("\\[(.*?)\\]").matcher(title)
        if (matcher.find()) {
            val source = matcher.group(1)?.lowercase() ?: return null
            return when {
                source.contains("миллер") -> "miller"
                source.contains("цветков") -> "tsvetkov"
                source.contains("ванга") -> "vanga"
                source.contains("фрейд") -> "freud"
                source.contains("ислам") -> "islamic"
                source.contains("юнона") -> "juno"
                else -> source.replace(" ", "_")
            }
        }
        return null
    }

    private fun removeSourceFromTitle(title: String): String {
        return title.replace(Regex("\\[.*?\\]"), "").trim()
    }
}

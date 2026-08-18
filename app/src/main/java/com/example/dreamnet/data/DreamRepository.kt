package com.example.dreamnet.data

import android.content.Context
import android.util.Log
import com.example.dreamnet.BuildConfig
import com.example.dreamnet.R
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.flow.Flow

class DreamRepository(
    private val dreamDao: DreamDao, 
    private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val importer = ExternalDatabaseImporter(dreamDao, context)

    private val geminiKeys = listOf(
        BuildConfig.GEMINI_API_KEY,
        BuildConfig.GEMINI_API_KEY_2,
        BuildConfig.GEMINI_API_KEY_3,
        BuildConfig.GEMINI_API_KEY_OLD,
        BuildConfig.GEMINI_API_KEY_EXTRA,
        BuildConfig.GEMINI_API_KEY_6
    ).map { it.trim() }.filter { it.isNotEmpty() }

    suspend fun getDreamInterpretation(query: String, language: String): InterpretationResult {
        Log.d("DreamNet", "SEARCH START: '$query', LANG: $language")
        val normalized = normalizeWord(query)
        
        // 1. Try Local Database First (Case Insensitive)
        val localMatch = try {
            searchDreamInternal(query, language)
        } catch (e: Exception) {
            Log.e("DreamNet", "DATABASE SEARCH ERROR", e)
            null
        }

        if (localMatch != null && localMatch.meanings.isNotEmpty()) {
            Log.d("DreamNet", "LOCAL CONCEPT FOUND: ${localMatch.entry.word}. Triggering AI Analysis...")
            val aiResponse = try {
                generateFullAiInterpretation(query, language, listOf(localMatch))
            } catch (e: Exception) {
                Log.e("DreamNet", "AI ANALYSIS FAILED", e)
                null
            }
            
            return InterpretationResult.Local(
                result = localMatch,
                aiSummary = aiResponse?.optString("interpretation"),
                adviceDo = aiResponse?.optString("adviceDo") ?: localMatch.meanings.firstOrNull { !it.adviceDo.isNullOrBlank() }?.adviceDo,
                adviceDont = aiResponse?.optString("adviceDont") ?: localMatch.meanings.firstOrNull { !it.adviceDont.isNullOrBlank() }?.adviceDont,
                realStories = aiResponse?.optString("realStories") ?: localMatch.meanings.find { !it.context.isNullOrBlank() }?.context
            )
        }

        // 2. Check AI Cache
        try {
            val cacheKey = "${normalized}_${language}"
            val cachedAi = dreamDao.getAiCache(cacheKey)
            if (cachedAi != null) {
                Log.d("DreamNet", "CACHE RESULT: FOUND")
                val json = JSONObject(cachedAi.interpretation)
                return InterpretationResult.AI(
                    interpretation = json.getString("interpretation"),
                    authorMeanings = parseAuthorMeanings(json.optJSONArray("authors")),
                    adviceDo = json.optString("adviceDo"),
                    adviceDont = json.optString("adviceDont"),
                    realStories = json.optString("realStories")
                )
            }
        } catch (e: Exception) {
            Log.e("DreamNet", "CACHE LOOKUP ERROR", e)
        }

        // 3. AI Fallback (if local match failed or has no meanings)
        Log.d("DreamNet", "LOCAL MATCH FAILED. Attempting AI Fallback for '$query'...")
        val localSymbols = findKnownSymbols(query, language)
        
        return try {
            val aiResponse = generateFullAiInterpretation(query, language, localSymbols)
            if (aiResponse != null) {
                val cacheKey = "${normalized}_${language}"
                dreamDao.insertAiCache(AiInterpretationCache(cacheKey, aiResponse.toString()))
                InterpretationResult.AI(
                    interpretation = aiResponse.getString("interpretation"),
                    authorMeanings = parseAuthorMeanings(aiResponse.optJSONArray("authors")),
                    adviceDo = aiResponse.optString("adviceDo"),
                    adviceDont = aiResponse.optString("adviceDont"),
                    realStories = aiResponse.optString("realStories"),
                    isContextual = localSymbols.isNotEmpty()
                )
            } else {
                Log.w("DreamNet", "AI response was null for query: $query")
                InterpretationResult.NotFound
            }
        } catch (e: Exception) {
            Log.e("DreamNet", "AI FALLBACK FAILED", e)
            if (e is java.io.IOException || e is java.net.SocketTimeoutException) {
                InterpretationResult.Error(R.string.error_no_internet)
            } else {
                InterpretationResult.Error(R.string.error_unknown)
            }
        }
    }

    private fun parseAuthorMeanings(jsonArray: JSONArray?): List<AiAuthorMeaning> {
        if (jsonArray == null) return emptyList()
        val list = mutableListOf<AiAuthorMeaning>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(AiAuthorMeaning(obj.getString("author"), obj.getString("meaning")))
        }
        return list
    }

    private suspend fun searchDreamInternal(query: String, language: String): DreamSearchResult? {
        val normalized = normalizeWord(query)
        val qTrim = query.trim()
        
        // Exact word (Room COLLATE NOCASE)
        val exactEntry = dreamDao.getEntryByWord(qTrim, language)
        if (exactEntry != null) return exactEntry

        // Variant match
        val variantMatch = dreamDao.findExactMatch(normalized, language)
        if (variantMatch != null) return variantMatch

        // FTS Search
        if (normalized.length >= 2) {
            try {
                val ftsResults = dreamDao.searchFts("$normalized*", language)
                val relevant = ftsResults.find { res ->
                    res.entry.word.equals(normalized, true) || 
                    res.variants.any { v -> v.normalizedVariant.equals(normalized, true) }
                }
                if (relevant != null) return relevant
            } catch (e: Exception) { }
        }

        // Fuzzy LIKE Search
        try {
            val fuzzyResults = dreamDao.searchFuzzy(normalized, language)
            if (fuzzyResults.isNotEmpty()) {
                val first = fuzzyResults.first()
                if (first.entry.word.contains(normalized, true)) return first
            }
        } catch (e: Exception) { }

        return null
    }

    private suspend fun findKnownSymbols(query: String, language: String): List<DreamSearchResult> {
        val words = query.split(" ", ",", ".", ";", "\n").filter { it.length >= 2 }
        val results = mutableListOf<DreamSearchResult>()
        for (word in words) {
            try {
                val match = dreamDao.getEntryByWord(word.trim(), language) 
                    ?: dreamDao.findExactMatch(normalizeWord(word), language)
                if (match != null) results.add(match)
            } catch (e: Exception) { }
        }
        return results.distinctBy { it.entry.linkedEntryGroupId }
    }

    private suspend fun generateFullAiInterpretation(
        query: String, 
        language: String, 
        localSymbols: List<DreamSearchResult>
    ): JSONObject? {
        val contextInfo = if (localSymbols.isNotEmpty()) {
            "LOCAL KNOWLEDGE FOUND IN DB:\n" +
            localSymbols.joinToString("\n") { symbol ->
                val meanings = symbol.meanings.joinToString("; ") { it.meaning }
                "${symbol.entry.word}: $meanings"
            }
        } else "No exact local meanings found. Search your memory."

        val systemPrompt = """
            You are 'DreamNet AI'. Expert bibliographer and dream analyst.
            Language: $language.
            
            GOAL: Analyze user's dream and provide classic dreambook views.
            
            INSTRUCTIONS:
            1. Create a list of interpretations from: Miller, Vanga, Nostradamus, Tsvetkov, Ibn Sirin, Loff, Hasse, Meneghetti, Esoteric.
            2. Synthesize these into a master "interpretation" narrative.
            3. Provide advice (adviceDo and adviceDont) and realStories (manifestations).
            
            REQUIRED JSON FORMAT:
            {
              "authors": [
                {"author": "Сонник Миллера", "meaning": "..."},
                {"author": "Сонник Ванги", "meaning": "..."},
                {"author": "Сонник Нострадамуса", "meaning": "..."},
                {"author": "Сонник Цветкова", "meaning": "..."},
                {"author": "Исламский сонник (Ибн Сирин)", "meaning": "..."},
                {"author": "Сонник Лоффа", "meaning": "..."},
                {"author": "Сонник Хассе", "meaning": "..."},
                {"author": "Сонник Менегетти", "meaning": "..."},
                {"author": "Эзотерический сонник", "meaning": "..."}
              ],
              "interpretation": "FULL combined psychological and symbolic analysis",
              "adviceDo": "Advice on what to do",
              "adviceDont": "Advice on what to avoid",
              "realStories": "Real life manifestation examples"
            }
            
            Tone: Professional. STRICT JSON. NO MARKDOWN. ONLY THE JSON BLOCK.
            
            $contextInfo
            
            USER DREAM: "$query"
        """.trimIndent()

        // Try Gemini keys
        for (key in geminiKeys) {
            if (key.startsWith("sk-or-v1")) continue // Skip OpenRouter key in Gemini loop
            
            try {
                Log.d("DreamNet", "Attempting Gemini key starting with ${key.take(8)}...")
                val model = GenerativeModel(modelName = "gemini-1.5-flash", apiKey = key)
                val response = withTimeoutOrNull(25000) {
                    model.generateContent(systemPrompt).text?.trim()
                }

                if (response != null) {
                    val extractedJson = extractJson(response)
                    if (extractedJson != null) return extractedJson
                }
            } catch (e: Exception) {
                Log.e("DreamNet", "Gemini key failed: ${e.message}")
            }
        }

        // Try OpenRouter Fallback
        val orKey = BuildConfig.OPENROUTER_API_KEY
        if (orKey.isNotEmpty()) {
            try {
                Log.d("DreamNet", "All Gemini failed. Attempting OpenRouter...")
                val response = callOpenRouter(systemPrompt, orKey)
                if (response != null) {
                    val extractedJson = extractJson(response)
                    if (extractedJson != null) return extractedJson
                }
            } catch (e: Exception) {
                Log.e("DreamNet", "OpenRouter failed: ${e.message}")
            }
        }

        return null
    }

    private fun extractJson(raw: String): JSONObject? {
        try {
            // Find first '{' and last '}'
            val start = raw.indexOf("{")
            val end = raw.lastIndexOf("}")
            if (start != -1 && end != -1 && end > start) {
                val jsonStr = raw.substring(start, end + 1)
                return JSONObject(jsonStr)
            }
        } catch (e: Exception) {
            Log.e("DreamNet", "JSON EXTRACTION FAILED", e)
        }
        return null
    }

    private suspend fun callOpenRouter(prompt: String, apiKey: String): String? {
        val json = JSONObject().apply {
            put("model", "google/gemini-flash-1.5")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://dreamnet.pro")
            .header("X-Title", "DreamNet Pro")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("DreamNet", "OpenRouter HTTP Error: ${response.code}")
                        return@withContext null
                    }
                    val body = response.body?.string() ?: return@withContext null
                    JSONObject(body).getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            } catch (e: Exception) {
                Log.e("DreamNet", "OpenRouter Network Error", e)
                null
            }
        }
    }

    private fun normalizeWord(word: String): String {
        return word.lowercase().trim().replace("ё", "е")
    }

    suspend fun syncLanguage(currentEntry: DreamEntry, targetLanguage: String): DreamSearchResult? {
        val groupId = currentEntry.linkedEntryGroupId ?: return null
        return dreamDao.getEntryByGroupAndLanguage(groupId, targetLanguage)
    }

    suspend fun prepopulateDatabase() {
        val count = dreamDao.getEntryCount()
        Log.d("DreamNet", "Current DB entry count: $count")
        
        // Ensure even existing databases get updated if they missed the large import
        if (count < 25000) { 
            Log.d("DreamNet", "Importing data from assets...")
            withContext(Dispatchers.IO) {
                try {
                    DreamSourceManager.sources.forEach { dreamDao.insertSource(it) }
                    dreamDao.insertSource(DreamSource(id = "dreamnet_base", name = "DreamNet Base", priority = 1))

                    val assets = context.assets.list("") ?: emptyArray()
                    val files = assets.filter { it.endsWith(".json") && (it.startsWith("dreams_batch_") || it.startsWith("dreams_master_")) }
                    
                    var groupIdCounter: Long = 500000
                    files.forEach { fileName ->
                        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                        if (fileName == "dreams_master_desktop.json") {
                            importExternalFormat(jsonString)
                        } else if (fileName.startsWith("dreams_master_")) {
                            importMasterFormat(jsonString)
                        } else {
                            groupIdCounter = importLegacyFormat(jsonString, "dreamnet_base", groupIdCounter)
                        }
                    }
                    Log.d("DreamNet", "Database import finished.")
                } catch (e: Exception) {
                    Log.e("DreamNet", "PREPOPULATION FAILED", e)
                }
            }
        }
    }

    private suspend fun importExternalFormat(jsonString: String) {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val ruTitle = obj.optString("ru_title", "").trim()
            val ruText = obj.optString("ru_text", "").trim()

            if (ruTitle.isEmpty()) continue

            val sourceId = extractSourceId(ruTitle)
            val cleanWord = removeSourceFromTitle(ruTitle)
            
            var entryId = dreamDao.getEntryByWord(cleanWord, "Русский")?.entry?.id
            if (entryId == null) {
                entryId = dreamDao.insertEntry(DreamEntry(word = cleanWord, language = "Русский", linkedEntryGroupId = 300000L + i))
            }

            if (ruText.isNotEmpty()) {
                dreamDao.insertMeaning(DreamMeaning(dreamEntryId = entryId, sourceId = sourceId ?: "external_desktop", meaning = ruText, language = "Русский"))
            }
            dreamDao.insertVariant(DreamVariant(dreamEntryId = entryId, variant = cleanWord, normalizedVariant = normalizeWord(cleanWord)))
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
                source.contains("нострадамус") -> "nostradamus"
                source.contains("лофф") -> "loff"
                source.contains("хассе") -> "hasse"
                source.contains("менегетти") -> "meneghetti"
                source.contains("эзотерич") -> "esoteric"
                else -> source.replace(" ", "_")
            }
        }
        return null
    }

    private fun removeSourceFromTitle(title: String): String {
        return title.replace(Regex("\\[.*?\\]"), "").trim()
    }

    private suspend fun importMasterFormat(jsonString: String) {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val groupObj = jsonArray.getJSONObject(i)
            val groupId = groupObj.optLong("groupId", -1L)
            val conceptsArray = groupObj.getJSONArray("concepts")
            for (j in 0 until conceptsArray.length()) {
                val concept = conceptsArray.getJSONObject(j)
                val lang = concept.getString("lang")
                val word = concept.getString("word").trim()
                
                var entryId = dreamDao.getEntryByWord(word, lang)?.entry?.id
                if (entryId == null) {
                    entryId = dreamDao.insertEntry(DreamEntry(word = word, language = lang, linkedEntryGroupId = if (groupId != -1L) groupId else null))
                }
                
                val meanings = concept.getJSONArray("meanings")
                for (m in 0 until meanings.length()) {
                    val mObj = meanings.getJSONObject(m)
                    dreamDao.insertMeaning(DreamMeaning(
                        dreamEntryId = entryId, 
                        sourceId = mObj.optString("sourceId", "dreamnet_base"), 
                        meaning = mObj.getString("meaning"), 
                        adviceDo = mObj.optString("adviceDo"), 
                        adviceDont = mObj.optString("adviceDont"), 
                        context = mObj.optString("context"), 
                        language = lang
                    ))
                }
            }
        }
    }

    private suspend fun importLegacyFormat(jsonString: String, defaultSourceId: String, startGroupId: Long): Long {
        var currentGroupId = startGroupId
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val wordRu = obj.optString("word", "").trim()
            if (wordRu.isEmpty()) continue
            
            var entryRuId = dreamDao.getEntryByWord(wordRu, "Русский")?.entry?.id
            if (entryRuId == null) {
                entryRuId = dreamDao.insertEntry(DreamEntry(word = wordRu, language = "Русский", linkedEntryGroupId = currentGroupId))
            }
            dreamDao.insertMeaning(DreamMeaning(dreamEntryId = entryRuId, sourceId = defaultSourceId, meaning = obj.optString("meaningRu"), adviceDo = obj.optString("adviceDoRu"), adviceDont = obj.optString("adviceDontRu"), context = obj.optString("realStoriesRu"), language = "Русский"))
            currentGroupId++
        }
        return currentGroupId
    }

    private fun generateSearchVariants(word: String, language: String): Set<String> {
        val variants = mutableSetOf(word)
        val w = word.lowercase().trim()
        when (language) {
            "Русский" -> {
                if (w.length > 3) {
                    val base = w.dropLast(1)
                    val last = w.takeLast(1)
                    when (last) {
                        "а", "я" -> variants.addAll(listOf(base + "ы", base + "и", base + "у", base + "ой", base + "е", base + "ам", base + "ами"))
                        "о", "е" -> variants.addAll(listOf(base + "а", base + "я", base + "у", base + "ом", base + "ем", base + "ах", base + "ами"))
                        "ь" -> variants.addAll(listOf(base + "я", base + "ю", base + "ем", base + "и"))
                        else -> variants.addAll(listOf(w + "а", w + "у", w + "ом", w + "е", w + "ы", w + "ов"))
                    }
                }
            }
            "English" -> {
                if (w.endsWith("y")) variants.add(w.dropLast(1) + "ies")
                else if (w.endsWith("s") || w.endsWith("ch") || w.endsWith("sh")) variants.add(w + "es")
                else variants.add(w + "s")
            }
            "O'zbek" -> {
                variants.addAll(listOf(w + "ni", w + "lar", w + "ning", w + "da"))
            }
        }
        return variants
    }

    suspend fun getAllSources(): List<DreamSource> = dreamDao.getAllSources()
    fun getAllDreams(): Flow<List<DreamEntry>> = dreamDao.getAllEntries()
    fun getSelectedLanguage(): String {
        val prefs = context.getSharedPreferences("dreamnet_prefs", Context.MODE_PRIVATE)
        return prefs.getString("selected_language", "Русский") ?: "Русский"
    }
    fun saveSelectedLanguage(lang: String) {
        val prefs = context.getSharedPreferences("dreamnet_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_language", lang).apply()
    }
}

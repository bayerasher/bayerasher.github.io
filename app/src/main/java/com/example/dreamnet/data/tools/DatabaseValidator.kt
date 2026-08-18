package com.example.dreamnet.data.tools

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

/**
 * Tool for validating dream database JSON files before build or import.
 */
class DatabaseValidator(private val context: Context) {

    data class ValidationReport(
        val totalFiles: Int,
        val totalRecords: Int,
        val uniqueConcepts: Int,
        val totalSearchableVariants: Int,
        val errors: List<String>,
        val stats: Map<String, Int>
    )

    fun validateAll(): ValidationReport {
        val assets = context.assets.list("") ?: return ValidationReport(0, 0, 0, 0, listOf("No assets found"), emptyMap())
        val files = assets.filter { it.endsWith(".json") && (it.startsWith("dreams_batch_") || it.startsWith("dreams_master_")) }
        
        var totalRecords = 0
        var totalSearchableVariants = 0
        val conceptSet = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        val langStats = mutableMapOf("Русский" to 0, "English" to 0, "O'zbek" to 0)

        files.forEach { fileName ->
            try {
                val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    
                    if (fileName.startsWith("dreams_master_")) {
                        // Master format validation
                        val groupId = obj.optLong("groupId", -1)
                        val concepts = obj.getJSONArray("concepts")
                        for (j in 0 until concepts.length()) {
                            val concept = concepts.getJSONObject(j)
                            val lang = concept.getString("lang")
                            val word = concept.getString("word")
                            conceptSet.add("$groupId:$lang:$word")
                            
                            val variants = concept.optJSONArray("variants")
                            if (variants != null) {
                                totalSearchableVariants += variants.length()
                            } else {
                                totalSearchableVariants += generateSearchVariants(word, lang).size
                            }
                            langStats[lang] = (langStats[lang] ?: 0) + 1
                        }
                    } else {
                        // Legacy format validation
                        val word = obj.optString("word")
                        if (word.isNullOrBlank()) {
                            errors.add("File $fileName, record $i: Missing 'word' field")
                            continue
                        }
                        totalRecords++
                        conceptSet.add("legacy:$word")
                        
                        // RU
                        totalSearchableVariants += generateSearchVariants(word, "Русский").size
                        langStats["Русский"] = (langStats["Русский"] ?: 0) + 1
                        
                        // EN
                        if (obj.has("meaningEn")) {
                            totalSearchableVariants += generateSearchVariants(word, "English").size
                            langStats["English"] = (langStats["English"] ?: 0) + 1
                        }
                        
                        // UZ
                        if (obj.has("meaningUz")) {
                            totalSearchableVariants += generateSearchVariants(word, "O'zbek").size
                            langStats["O'zbek"] = (langStats["O'zbek"] ?: 0) + 1
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add("Critical error in file $fileName: ${e.message}")
            }
        }

        return ValidationReport(
            totalFiles = files.size,
            totalRecords = totalRecords,
            uniqueConcepts = conceptSet.size,
            totalSearchableVariants = totalSearchableVariants,
            errors = errors,
            stats = langStats
        )
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
                        "а", "я" -> variants.addAll(listOf(base + "ы", base + "и", base + "у", base + "ой", base + "е"))
                        "о", "е" -> variants.addAll(listOf(base + "а", base + "я", base + "у", base + "ом", base + "ем"))
                    }
                }
            }
            "English" -> {
                if (w.endsWith("y")) variants.add(w.dropLast(1) + "ies")
                else variants.add(w + "s")
            }
            "O'zbek" -> {
                variants.addAll(listOf(w + "ni", w + "lar", w + "ning"))
            }
        }
        return variants
    }

    fun printReport() {
        val report = validateAll()
        Log.d("DB_VALIDATOR", "--- DREAMNET DATABASE REPORT ---")
        Log.d("DB_VALIDATOR", "Total Files: ${report.totalFiles}")
        Log.d("DB_VALIDATOR", "Unique Concepts (Linked): ${report.uniqueConcepts}")
        Log.d("DB_VALIDATOR", "Language Distribution: ${report.stats}")
        Log.d("DB_VALIDATOR", "TOTAL SEARCHABLE VARIANTS (FTS): ${report.totalSearchableVariants}")
        Log.d("DB_VALIDATOR", "Total Errors: ${report.errors.size}")
        Log.d("DB_VALIDATOR", "--------------------------------")
    }
}

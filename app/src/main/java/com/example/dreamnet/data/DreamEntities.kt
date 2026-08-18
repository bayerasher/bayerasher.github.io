package com.example.dreamnet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class SourceType {
    PRIMARY_SOURCE, TRUSTED_EDITION, SPECIALIZED_SITE, AGGREGATOR, UNVERIFIED, AI_GENERATED, DREAM_BOOK
}

enum class VerificationLevel {
    HIGH, MEDIUM, LOW
}

enum class CopyrightStatus {
    PUBLIC_DOMAIN, LICENSED, RESTRICTED, UNKNOWN
}

@Entity(tableName = "dream_sources")
data class DreamSource(
    @PrimaryKey val id: String,
    val name: String,
    val author: String? = null,
    val bookName: String? = null,
    val description: String? = null,
    val priority: Int = 10,
    val enabled: Boolean = true,
    val sourceType: SourceType = SourceType.UNVERIFIED,
    val verificationLevel: VerificationLevel = VerificationLevel.LOW,
    val copyrightStatus: CopyrightStatus = CopyrightStatus.UNKNOWN,
    val cacheDurationDays: Int = 30
)

@Entity(
    tableName = "dream_entries",
    indices = [
        Index(value = ["word", "language"], unique = true), 
        Index(value = ["linkedEntryGroupId"])
    ]
)
data class DreamEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val language: String, // "Русский", "English", "O'zbek"
    val categoryId: String? = null,
    val linkedEntryGroupId: Long? = null, // The STABLE Concept ID
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dream_variants",
    foreignKeys = [
        ForeignKey(
            entity = DreamEntry::class,
            parentColumns = ["id"],
            childColumns = ["dreamEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["dreamEntryId"]), Index(value = ["normalizedVariant"])]
)
data class DreamVariant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dreamEntryId: Long,
    val variant: String,
    val normalizedVariant: String
)

@Fts4(contentEntity = DreamVariant::class)
@Entity(tableName = "dream_variants_fts")
data class DreamVariantFts(
    val variant: String,
    val normalizedVariant: String
)

@Entity(
    tableName = "dream_meanings",
    foreignKeys = [
        ForeignKey(
            entity = DreamEntry::class,
            parentColumns = ["id"],
            childColumns = ["dreamEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DreamSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["dreamEntryId"]), Index(value = ["sourceId"])]
)
data class DreamMeaning(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dreamEntryId: Long,
    val sourceId: String,
    val meaning: String,
    val adviceDo: String? = null,
    val adviceDont: String? = null,
    val context: String? = null,
    val language: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val title: String,
    val content: String,
    val date: String,
    val aiInterpretation: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class DreamSearchResult(
    @Embedded val entry: DreamEntry,
    @Relation(
        parentColumn = "id",
        entityColumn = "dreamEntryId"
    )
    val meanings: List<DreamMeaning>,
    @Relation(
        parentColumn = "id",
        entityColumn = "dreamEntryId"
    )
    val variants: List<DreamVariant>
)

@Dao
interface DreamDao {
    @Transaction
    @Query("SELECT * FROM dream_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): DreamSearchResult?

    @Transaction
    @Query("SELECT * FROM dream_entries WHERE word = :word COLLATE NOCASE AND language = :language LIMIT 1")
    suspend fun getEntryByWord(word: String, language: String): DreamSearchResult?

    @Transaction
    @Query("SELECT * FROM dream_entries WHERE linkedEntryGroupId = :groupId AND language = :language LIMIT 1")
    suspend fun getEntryByGroupAndLanguage(groupId: Long, language: String): DreamSearchResult?

    @Transaction
    @Query("""
        SELECT e.* FROM dream_entries e
        JOIN dream_variants v ON e.id = v.dreamEntryId
        WHERE e.language = :language AND (v.variant = :query COLLATE NOCASE OR v.normalizedVariant = :query COLLATE NOCASE)
        LIMIT 1
    """)
    suspend fun findExactMatch(query: String, language: String): DreamSearchResult?

    @Transaction
    @Query("""
        SELECT e.* FROM dream_entries e
        JOIN dream_variants v ON e.id = v.dreamEntryId
        JOIN dream_variants_fts fts ON v.id = fts.rowid
        WHERE e.language = :language AND dream_variants_fts MATCH :query
        LIMIT 20
    """)
    suspend fun searchFts(query: String, language: String): List<DreamSearchResult>

    @Transaction
    @Query("""
        SELECT e.* FROM dream_entries e
        JOIN dream_variants v ON e.id = v.dreamEntryId
        WHERE e.language = :language AND (v.variant LIKE '%' || :query || '%' COLLATE NOCASE OR v.normalizedVariant LIKE '%' || :query || '%' COLLATE NOCASE)
        LIMIT 20
    """)
    suspend fun searchFuzzy(query: String, language: String): List<DreamSearchResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: DreamSource)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DreamEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: DreamVariant)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeaning(meaning: DreamMeaning)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiCache(cache: AiInterpretationCache)

    @Query("SELECT * FROM ai_interpretation_cache WHERE queryKey = :key LIMIT 1")
    suspend fun getAiCache(key: String): AiInterpretationCache?

    @Query("SELECT COUNT(*) FROM dream_entries")
    suspend fun getEntryCount(): Int

    @Query("SELECT * FROM dream_sources")
    suspend fun getAllSources(): List<DreamSource>

    @Query("SELECT * FROM dream_entries ORDER BY word ASC")
    fun getAllEntries(): Flow<List<DreamEntry>>
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE userId = :userId ORDER BY timestamp DESC")
    fun getEntriesByUserId(userId: String): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): DiaryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DiaryEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: DiaryEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)
}

@Entity(tableName = "ai_interpretation_cache")
data class AiInterpretationCache(
    @PrimaryKey val queryKey: String, // normalizedQuery + language
    val interpretation: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiAuthorMeaning(val author: String, val meaning: String)

sealed class InterpretationResult {
    data class Local(
        val result: DreamSearchResult, 
        val aiSummary: String? = null,
        val adviceDo: String? = null,
        val adviceDont: String? = null,
        val realStories: String? = null
    ) : InterpretationResult()
    data class AI(
        val interpretation: String, 
        val authorMeanings: List<AiAuthorMeaning> = emptyList(),
        val adviceDo: String? = null,
        val adviceDont: String? = null,
        val realStories: String? = null,
        val isContextual: Boolean = false
    ) : InterpretationResult()
    object NotFound : InterpretationResult()
    data class Error(val messageResId: Int) : InterpretationResult()
}

@Database(
    entities = [
        DreamSource::class,
        DreamEntry::class,
        DreamVariant::class,
        DreamVariantFts::class,
        DreamMeaning::class,
        DiaryEntryEntity::class,
        AiInterpretationCache::class
    ],
    version = 8,
    exportSchema = false
)
abstract class DreamDatabase : RoomDatabase() {
    abstract fun dreamDao(): DreamDao
    abstract fun diaryDao(): DiaryDao
}

package com.example.dreamnet.di

import android.content.Context
import androidx.room.Room
import com.example.dreamnet.data.DiaryDao
import com.example.dreamnet.data.DreamDao
import com.example.dreamnet.data.DreamDatabase
import com.example.dreamnet.data.DreamRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DreamDatabase {
        return Room.databaseBuilder(
            context,
            DreamDatabase::class.java, "dream_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideDreamDao(db: DreamDatabase): DreamDao = db.dreamDao()

    @Provides
    fun provideDiaryDao(db: DreamDatabase): DiaryDao = db.diaryDao()

    @Provides
    @Singleton
    fun provideDreamRepository(
        dao: DreamDao,
        @ApplicationContext context: Context
    ): DreamRepository {
        return DreamRepository(dao, context)
    }
}

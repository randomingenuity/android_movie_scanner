package com.movie.scanner.di

import android.content.Context
import androidx.room.Room
import com.movie.scanner.data.local.AppDatabase
import com.movie.scanner.data.local.MIGRATION_1_2
import com.movie.scanner.data.local.MIGRATION_2_3
import com.movie.scanner.data.local.MIGRATION_3_4
import com.movie.scanner.data.local.MovieDao
import com.movie.scanner.data.remote.GeminiApi
import com.movie.scanner.data.remote.OpenAiApi
import com.movie.scanner.data.remote.TmdbApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    @Provides
    @Singleton
    fun provideTmdbApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TmdbApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): GeminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApi::class.java)

    @Provides
    @Singleton
    fun provideOpenAiApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): OpenAiApi = Retrofit.Builder()
        .baseUrl("https://api.openai.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenAiApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "movie_scanner.db",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao = database.movieDao()
}

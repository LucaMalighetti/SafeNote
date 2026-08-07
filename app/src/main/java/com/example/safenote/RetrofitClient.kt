package com.example.safenote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Sostituisci questo URL con quello fornito da Render (es. https://safenote.onrender.com/)
    private const val BASE_URL = "https://safenote-szau.onrender.com/"

    val instance: ApiService by lazy {
        val logging = okhttp3.logging.HttpLoggingInterceptor()
        logging.setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BODY)

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}

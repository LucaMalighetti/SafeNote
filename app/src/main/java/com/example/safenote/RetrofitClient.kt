package com.example.safenote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Sostituisci questo URL con quello fornito da Render (es. https://safenote.onrender.com/)
    private const val BASE_URL = "https://safenote-szau.onrender.com/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}

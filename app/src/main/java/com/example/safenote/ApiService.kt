package com.example.safenote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("auth/login-direct")
    suspend fun loginDirect(@Body data: Map<String, String>): Response<Map<String, Any>>

    @POST("auth/request-code")
    suspend fun requestCode(@Body data: Map<String, String>): Response<Map<String, Any>>

    @POST("auth/verify-code")
    suspend fun verifyCode(@Body data: Map<String, String>): Response<Map<String, Any>>

    @GET("photos/{className}")
    suspend fun getPhotos(@Path("className") className: String): Response<List<SharedPhoto>>

    @DELETE("photos/{id}")
    suspend fun deletePhoto(@Path("id") id: String): Response<Map<String, Any>>

    @Multipart
    @POST("photos")
    suspend fun uploadPhoto(
        @Part("owner") owner: RequestBody,
        @Part("className") className: RequestBody,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("tags") tags: RequestBody,
        @Part photos: List<MultipartBody.Part>
    ): Response<SharedPhoto>

    @POST("requests")
    suspend fun sendRequest(@Body request: ViewRequest): Response<ViewRequest>

    @GET("requests/{username}")
    suspend fun getRequests(@Path("username") username: String): Response<List<ViewRequest>>

    @PUT("requests/{id}")
    suspend fun updateRequestStatus(
        @Path("id") id: String,
        @Body status: Map<String, String>
    ): Response<ViewRequest>
}

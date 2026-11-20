package com.wanderai.travelplanner

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleMapsApiService {

    @GET("maps/api/distancematrix/json")
    suspend fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("mode") mode: String = "driving",
        @Query("units") units: String = "imperial",
        @Query("key") apiKey: String
    ): Response<DistanceMatrixResponse>
}
package com.example.traukiniuwiget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews




import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*

class TraukinioGrafikas : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            GlobalScope.launch(Dispatchers.IO) {
                val response = fetchTrainData()
                withContext(Dispatchers.Main) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, response)
                }
            }
        }
    }
}

// Pakeisk fetchTrainData, kad grąžintų String ir būtų suspend funkcija
suspend fun fetchTrainData(): String {
    return withContext(Dispatchers.IO) {
        try {
            val proxyPrefix = "https://corsproxy.io/?"
            val originalUrl = "https://bilietas.ltglink.lt/api/v2021/lt-lt/journeys/search?" +
                    "departureDate=2025-05-15&isPartOfRoundtrip=false&currencyId=CURRENCY.EUR" +
                    "&Passengers=BONUS_SCHEME_GROUP.ADULT%2C1&EuRailInterRailCodes=" +
                    "&OriginStopId=11&DestinationStopId=16&IsOutbound=true" +
                    "&CheckPassengerSoldTogetherRules=true&IsGroupTicket=false"

            val url = URL(proxyPrefix + originalUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Referer", "https://bilietas.ltglink.lt/")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().readText()
            } else {
                "Klaida: $responseCode"
            }
        } catch (e: Exception) {
            "Išimtis: ${e.message}"
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    responseText: String,

) {
    val jsonObject = JSONObject(responseText)
    val journeysArray = jsonObject.getJSONArray("Journeys")
    var atsTekstas = "NeraRezautoto"
    for (i in 0 until journeysArray.length() ) {
        val journey = journeysArray.getJSONObject(i)
        val originCity = journey
            .getJSONObject("Origin")
            .getJSONObject("City")
            .getString("Name")

        val destinationCity = journey
            .getJSONObject("Destination")
            .getJSONObject("City")
            .getString("Name")

        val departureTime = journey
            .getJSONObject("Origin")
            .getString("ActualDepartureDateTime")
        val atvykimoLaikas = journey
            .getJSONObject("Destination")
            .getString("PlannedArrivalDateTime",)
        atsTekstas = "$originCity - $destinationCity,\n  $departureTime - $atvykimoLaikas"
        println("Kelionė: iš $originCity į $destinationCity, išvyksta $departureTime ")
    }
    val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
    views.setTextViewText(R.id.appwidget_text, atsTekstas)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

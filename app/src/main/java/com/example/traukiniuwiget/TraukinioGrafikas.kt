package com.example.traukiniuwiget
import java.time.LocalDate
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
//        button2.setOnClickListener {
//            // Veiksmas, kai paspaudžiamas mygtukas
//            println("Paspaudei mygtuką")
//        }


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

var choise1 = 11
var choise2 = 16

//val button2 = findViewById<Button>(R.id.button2)



suspend fun fetchTrainData(s: Int = 16,p: Int = 17): String {
    return withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()

            val year = today.year
            val month = today.monthValue
            val day = today.dayOfMonth  + 1
            val proxyPrefix = "https://corsproxy.io/?"
            val originalUrl = "https://bilietas.ltglink.lt/api/v2021/lt-lt/journeys/search" +
                    "?departureDate="+ year +"-"+ month+"-"+day+
                    "&isPartOfRoundtrip=false" +
                    "&currencyId=CURRENCY.EUR" +
                    "&Passengers=BONUS_SCHEME_GROUP.ADULT%2C1" +
                    "&EuRailInterRailCodes=" +
                    "&OriginStopId=$s" +
                    "&DestinationStopId=$p" +
                    "&IsOutbound=true" +
                    "&CheckPassengerSoldTogetherRules=true" +
                    "&IsGroupTicket=false"

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
    class Marsrutas{
        var atvykimoIsvykimolaikas = ""
        var kaina = ""
        var kelionesLaikas = ""
    }


    val jsonObject = JSONObject(responseText)
    val journeysArray = jsonObject.getJSONArray("Journeys")
    val laikai = Array(journeysArray.length()+4) { "" }

    var kryptis = ""
    for (i in journeysArray.length() - 1 downTo 0) {

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
            .split("T").toTypedArray()[1].dropLast(3)
        val atvykimoLaikas = journey
            .getJSONObject("Destination")
            .getString("PlannedArrivalDateTime")
            .split("T")
            .toTypedArray()[1].dropLast(3)
        kryptis="$originCity -> $destinationCity"

        val atsTekstas = "$departureTime -> $atvykimoLaikas"

        laikai[i] =  atsTekstas
        println("Kelionė: iš $originCity į $destinationCity, išvyksta $departureTime ")
    }
    val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
    views.setTextViewText(R.id.Marsrutas, kryptis )
    views.setTextViewText(R.id.pirmas,  laikai[0] )
    views.setTextViewText(R.id.antras,  laikai[1])
    views.setTextViewText(R.id.trecias,  laikai[2])
    views.setTextViewText(R.id.ketvirtas,laikai[3])
    views.setTextViewText(R.id.penktas,  laikai[4])

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

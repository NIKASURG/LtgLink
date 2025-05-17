package com.example.traukiniuwiget

import java.time.LocalDate
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews
import android.util.Log
import kotlinx.coroutines.*
import android.content.ComponentName

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.*


fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false

    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

const val ACTION_REVERSE = "ACTION_REVERSE"
const val ACTION_REFRESH = "ACTION_REFRESH"

var choise1 = 11
var choise2 = 16

class TraukinioGrafikas : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {

            val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)

            // Mygtukas: sukeičia stoteles
            val intentBtn = Intent(context, TraukinioGrafikas::class.java).apply {
                action = ACTION_REVERSE
            }
            val pendingIntentBtn = PendingIntent.getBroadcast(
                context,
                0,
                intentBtn,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.button, pendingIntentBtn)


            val intent = Intent(context, TraukinioGrafikas::class.java).apply {
                action = ACTION_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.button2, pendingIntent)


            val today = LocalDate.now()
            val year = today.year
            val month = String.format("%02d", today.monthValue)
            val day = String.format("%02d", today.dayOfMonth)

            val url = "https://bilietas.ltglink.lt/journeys?oStop=$choise1&dStop=$choise2&oDate=$year-$month-$day&fareClasses=BONUS_SCHEME_GROUP.ADULT,1"

            val urlIntent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(url)
            }

            val urlPendingIntent = PendingIntent.getActivity(
                context,
                0,
                urlIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.button3, urlPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)

            GlobalScope.launch(Dispatchers.IO) {
                if (isInternetAvailable(context)) {
                    val response = fetchTrainData()
                    withContext(Dispatchers.Main) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, response)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val viewsNoNet = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
                        viewsNoNet.setTextViewText(R.id.Marsrutas, "Be interneto")
                        appWidgetManager.updateAppWidget(appWidgetId, viewsNoNet)
                    }
                }
            }

        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, TraukinioGrafikas::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        when (intent.action) {
            ACTION_REVERSE -> {
                Log.d("Traukiniai", "Apsukamos stoteles")
                val laikinas = choise1
                choise1 = choise2
                choise2 = laikinas
            }
            ACTION_REFRESH -> {
                Log.d("Traukiniai", "Atnaujinami duomenys")
            }
            else -> return
        }

        GlobalScope.launch(Dispatchers.IO) {
            if (isInternetAvailable(context)) {
                val response = fetchTrainData()
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, response)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        val viewsNoNet = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
                        viewsNoNet.setTextViewText(R.id.Marsrutas, "Be interneto")
                        appWidgetManager.updateAppWidget(appWidgetId, viewsNoNet)
                    }
                }
            }
        }

    }
}

// ----- Duomenų gavimo funkcija -----

suspend fun fetchTrainData(s: Int = choise1, p: Int = choise2): String {
    return withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val year = today.year
            val month = String.format("%02d", today.monthValue)
            val day = today.dayOfMonth

            val proxyPrefix = "https://corsproxy.io/?"
            val originalUrl = "https://bilietas.ltglink.lt/api/v2021/lt-lt/journeys/search?departureDate=$year-$month-$day&isPartOfRoundtrip=false&currencyId=CURRENCY.EUR&Passengers=BONUS_SCHEME_GROUP.ADULT%2C1&EuRailInterRailCodes=&OriginStopId=$s&DestinationStopId=$p&IsOutbound=true&CheckPassengerSoldTogetherRules=true&IsGroupTicket=false"

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

// ----- Atnaujina widgeto tekstus -----

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    responseText: String
) {
    val jsonObject = JSONObject(responseText)
    val journeysArray = jsonObject.getJSONArray("Journeys")
    val laikai = Array(journeysArray.length() + 4) { "" }

    var kryptis = ""
    for (i in journeysArray.length() - 1 downTo 0) {
        val journey = journeysArray.getJSONObject(i)
        val originCity = journey.getJSONObject("Origin").getJSONObject("City").getString("Name")
        val destinationCity = journey.getJSONObject("Destination").getJSONObject("City").getString("Name")

        val departureTime = journey.getJSONObject("Origin").getString("ActualDepartureDateTime").split("T")[1].dropLast(3)
        val atvykimoLaikas = journey.getJSONObject("Destination").getString("PlannedArrivalDateTime").split("T")[1].dropLast(3)

        kryptis = "$originCity -> $destinationCity"
        laikai[i] = "$departureTime -> $atvykimoLaikas"
    }

    val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
    views.setTextViewText(R.id.Marsrutas, kryptis)
    views.setTextViewText(R.id.pirmas, laikai[0])
    views.setTextViewText(R.id.antras, laikai[1])
    views.setTextViewText(R.id.trecias, laikai[2])
    views.setTextViewText(R.id.ketvirtas, laikai[3])
    views.setTextViewText(R.id.penktas, laikai[4])

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

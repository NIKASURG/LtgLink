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
import com.example.traukiniuwiget.R
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri // Correct import for android.net.Uri
import android.content.SharedPreferences // Importuojame SharedPreferences

// Funkcija, patikrinanti interneto ryšį
fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false

    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// Veiksmų konstantos, naudojamos PendingIntent'uose
const val ACTION_REVERSE = "ACTION_REVERSE"
const val ACTION_REFRESH = "ACTION_REFRESH"

// Numatytosios reikšmės, jei SharedPreferences dar nėra išsaugotos (arba jei MainActivity dar nepaleista)
// Šios reikšmės bus naudojamos TIK tuo atveju, jei SharedPreferences neturi įrašų su KEY_CHOICE1/KEY_CHOICE2
const val FALLBACK_CHOICE1 = 11 // Kaišiadorių stotelės ID pavyzdys
const val FALLBACK_CHOICE2 = 16 // Kauno stotelės ID pavyzdys

// Pagrindinė App Widget'o klasė
class TraukinioGrafikas : AppWidgetProvider() {

    // Define unique request codes for your PendingIntents
    // Using distinct integers will resolve the ambiguity.
    companion object {
        private const val REQUEST_CODE_REVERSE = 1001
        private const val REQUEST_CODE_REFRESH = 1002
        private const val REQUEST_CODE_URL_BUTTON = 1003
        // You might need more if you have other interactive elements
    }

    // Šis metodas iškviečiamas, kai widget'as yra atnaujinamas (pvz., pridedamas, periodiškai)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Pereiname per visus widget'o ID
        for (appWidgetId in appWidgetIds) {
            // Nuskaitome choise1 ir choise2 iš SharedPreferences
            // Naudojame FALLBACK_CHOICE1/2 kaip numatytąsias reikšmes, jei MainActivity dar neįrašė
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentChoice1 = prefs.getInt(KEY_CHOICE1, FALLBACK_CHOICE1)
            val currentChoice2 = prefs.getInt(KEY_CHOICE2, FALLBACK_CHOICE2)

            // Sukuriame RemoteViews objektą, kuris atspindi widget'o išdėstymą
            val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)

            // Mygtukas: sukeičia stoteles (ACTION_REVERSE)
            val reverseIntent = Intent(context, TraukinioGrafikas::class.java).apply {
                action = ACTION_REVERSE
            }
            val pendingReverseIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REVERSE, // Changed to unique request code
                reverseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.button, pendingReverseIntent) // Priskiriame mygtukui ID: button

            // Mygtukas: atnaujina duomenis (ACTION_REFRESH)
            val refreshIntent = Intent(context, TraukinioGrafikas::class.java).apply {
                action = ACTION_REFRESH
            }
            val pendingRefreshIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REFRESH, // Changed to unique request code
                refreshIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.button2, pendingRefreshIntent) // Priskiriame mygtukui ID: button2

            // Mygtukas: atidaro bilietų pirkimo puslapį (button3)
            val today = LocalDate.now()
            val year = today.year
            val month = String.format("%02d", today.monthValue)
            val day = String.format("%02d", today.dayOfMonth)

            // Sukuriame URL su dabartinėmis choise1 ir choise2 reikšmėmis
            val url = "https://bilietas.ltglink.lt/journeys?oStop=$currentChoice1&dStop=$currentChoice2&oDate=$year-$month-$day&fareClasses=BONUS_SCHEME_GROUP.ADULT,1"

            val urlIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url) // Naudojame android.net.Uri
            }

            val urlPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_URL_BUTTON, // Changed to unique request code
                urlIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.button3, urlPendingIntent) // Priskiriame mygtukui ID: button3

            // Atnaujiname widget'ą su nustatytais vaizdais
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Paleidžiame duomenų gavimą asynchroniškai
            GlobalScope.launch(Dispatchers.IO) {
                if (isInternetAvailable(context)) {
                    // Perduodame dabartines choise1 ir choise2 reikšmes į fetchTrainData
                    val response = fetchTrainData(currentChoice1, currentChoice2)
                    withContext(Dispatchers.Main) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, response)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val viewsNoNet = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
                        viewsNoNet.setTextViewText(R.id.Marsrutas1, "Be interneto")
                        appWidgetManager.updateAppWidget(appWidgetId, viewsNoNet)
                    }
                }
            }
        }
    }

    // Šis metodas iškviečiamas, kai gaunamas broadcast'as (pvz., paspaudus mygtuką)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, TraukinioGrafikas::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        // Nuskaitome choise1 ir choise2 iš SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var choise1 = prefs.getInt(KEY_CHOICE1, FALLBACK_CHOICE1)
        var choise2 = prefs.getInt(KEY_CHOICE2, FALLBACK_CHOICE2)

        when (intent.action) {
            ACTION_REVERSE -> {
                Log.d("Traukiniai", "Apsukamos stoteles")
                // Sukeičiame reikšmes
                val temp = choise1
                choise1 = choise2
                choise2 = temp

                // Išsaugome atnaujintas reikšmes į SharedPreferences
                with(prefs.edit()) {
                    putInt(KEY_CHOICE1, choise1)
                    putInt(KEY_CHOICE2, choise2)
                    apply() // apply() išsaugo asynchroniškai
                }
                // Po reikšmių pakeitimo ir išsaugojimo, atnaujiname widget'ą, kad atspindėtų pokyčius
                // Tai svarbu, kad widget'as iškart parodytų naujas stoteles ir gautų naujus duomenis
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
                    // Atnaujiname URL mygtuko PendingIntent'ą su naujomis reikšmėmis
                    val today = LocalDate.now()
                    val year = today.year
                    val month = String.format("%02d", today.monthValue)
                    val day = String.format("%02d", today.dayOfMonth)
                    val url = "https://bilietas.ltglink.lt/journeys?oStop=$choise1&dStop=$choise2&oDate=$year-$month-$day&fareClasses=BONUS_SCHEME_GROUP.ADULT,1"
                    val urlIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url) // Naudojame android.net.Uri
                    }
                    val urlPendingIntent = PendingIntent.getActivity(
                        context,
                        REQUEST_CODE_URL_BUTTON, // Ensure this is also unique in onReceive
                        urlIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    views.setOnClickPendingIntent(R.id.button3, urlPendingIntent)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
            ACTION_REFRESH -> {
                Log.d("Traukiniai", "Atnaujinami duomenys")
                // Nereikia keisti choise1 ir choise2, tiesiog atnaujiname duomenis
            }
            else -> return // Jei veiksmas neatpažintas, išeiname
        }

        // Paleidžiame duomenų gavimą asynchroniškai su dabartinėmis (galbūt atnaujintomis) choise1 ir choise2 reikšmėmis
        GlobalScope.launch(Dispatchers.IO) {
            if (isInternetAvailable(context)) {
                val response = fetchTrainData(choise1, choise2) // Perduodame dabartines reikšmes
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, response)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        val viewsNoNet = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
                        viewsNoNet.setTextViewText(R.id.Marsrutas1, "Be interneto")
                        appWidgetManager.updateAppWidget(appWidgetId, viewsNoNet)
                    }
                }
            }
        }
    }
}

// Funkcija, gaunanti traukinių duomenis iš API
// Dabar ji priima stotelių ID kaip parametrus, o ne naudoja globalius kintamuosius
suspend fun fetchTrainData(s: Int, p: Int): String {
    return withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val year = today.year
            val month = String.format("%02d", today.monthValue)
            val day = today.dayOfMonth

            val proxyPrefix = "https://corsproxy.io/?"
            // Naudojame perduotus s ir p parametrus
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

// Funkcija, atnaujinanti widget'o tekstus pagal gautus duomenis
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    responseText: String
) {
    // Patikriname, ar gautas atsakymas yra JSON
    if (!responseText.startsWith("{") && !responseText.startsWith("[")) {
        val viewsError = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
        viewsError.setTextViewText(R.id.Marsrutas1, "Klaida: ${responseText}")
        viewsError.setTextViewText(R.id.pirmas, "")
        viewsError.setTextViewText(R.id.antras, "")
        viewsError.setTextViewText(R.id.trecias, "")
        viewsError.setTextViewText(R.id.ketvirtas, "")
        viewsError.setTextViewText(R.id.penktas, "")
        appWidgetManager.updateAppWidget(appWidgetId, viewsError)
        return
    }

    val jsonObject = JSONObject(responseText)
    val journeysArray = jsonObject.getJSONArray("Journeys")
    val laikai = Array(journeysArray.length() + 4) { "" } // Padidiname dydį, kad būtų vietos visiems laikams

    var kryptis = ""

    // Iteruojame per keliones atgal, kad gautume naujausius laikus į pradžią
    for (i in journeysArray.length() - 1 downTo 0) {
        val journey = journeysArray.getJSONObject(i)
        val originCity = journey.getJSONObject("Origin").getJSONObject("City").getString("Name")
        val destinationCity = journey.getJSONObject("Destination").getJSONObject("City").getString("Name")

        val departureTime = journey.getJSONObject("Origin").getString("ActualDepartureDateTime").split("T")[1].dropLast(3)
        val arrivalTime = journey.getJSONObject("Destination").getString("PlannedArrivalDateTime").split("T")[1].dropLast(3)

        kryptis = "$originCity ➞ $destinationCity"
        // Įrašome laikus į masyvą nuo pradžios
        if (i < laikai.size) { // Apsauga nuo masyvo ribų viršijimo
            laikai[i] = "$departureTime - $arrivalTime"
        }
    }

    val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
    views.setTextViewText(R.id.Marsrutas1, kryptis)
    views.setTextViewText(R.id.pirmas, laikai.getOrElse(0) { "" })
    views.setTextViewText(R.id.antras, laikai.getOrElse(1) { "" })
    views.setTextViewText(R.id.trecias, laikai.getOrElse(2) { "" })
    views.setTextViewText(R.id.ketvirtas, laikai.getOrElse(3) { "" })
    views.setTextViewText(R.id.penktas, laikai.getOrElse(4) { "" })
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
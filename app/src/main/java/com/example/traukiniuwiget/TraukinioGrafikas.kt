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
import org.json.JSONException // Būtinai importuokite JSONException
import com.example.traukiniuwiget.R // Pakeiskite į savo R failo kelią, jei reikia

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
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    responseText: String
) {
    // Pridedame detalesnį log'inimą, kad matytume gaunamą responseText
    Log.d("WidgetDebug", "updateAppWidget gavo responseText: [${responseText}]")
    Log.d("WidgetDebug", "responseText ilgis: ${responseText.length}")
    Log.d("WidgetDebug", "responseText.startsWith('{'): ${responseText.startsWith("{")}")
    Log.d("WidgetDebug", "responseText.startsWith('['): ${responseText.startsWith("[")}")

    // Patikriname, ar gautas atsakymas yra JSON pagal pradžios simbolius
    // Šis patikrinimas yra greitas būdas atmesti akivaizdžiai ne JSON eilutes.
    if (!responseText.trimStart().startsWith("{") && !responseText.trimStart().startsWith("[")) {
        Log.e("WidgetDebug", "Aptiktas ne JSON formatu pradedamas responseText (po trimStart): [${responseText}]")
        val viewsError = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
        // Rodome klaidą, kuri gali būti pati responseText, jei ji trumpa, arba bendrinę klaidą.
        val errorMessage = if (responseText.length < 50) responseText else "Netikėtas atsakas"
        viewsError.setTextViewText(R.id.Marsrutas1, "Klaida: $errorMessage")
        viewsError.setTextViewText(R.id.pirmas, "")
        viewsError.setTextViewText(R.id.antras, "")
        viewsError.setTextViewText(R.id.trecias, "")
        viewsError.setTextViewText(R.id.ketvirtas, "")
        viewsError.setTextViewText(R.id.penktas, "")
        appWidgetManager.updateAppWidget(appWidgetId, viewsError)
        return
    }

    val jsonObject: JSONObject // Deklaruojame jsonObject čia

    try {
        // Bandome konvertuoti responseText į JSONObject
        Log.d("WidgetDebug", "Bandoma konvertuoti į JSONObject: [${responseText}]")
        jsonObject = JSONObject(responseText)
    } catch (e: JSONException) {
        // Jei įvyksta JSONException konvertuojant pradinę eilutę
        Log.e("WidgetDebug", "JSONException konvertuojant pradinę eilutę: ${e.message}, responseText buvo: [${responseText}]")

        val viewsError = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
        viewsError.setTextViewText(R.id.Marsrutas1, "Klaida: Blogas atsako formatas")
        viewsError.setTextViewText(R.id.pirmas, "")
        viewsError.setTextViewText(R.id.antras, "")
        viewsError.setTextViewText(R.id.trecias, "")
        viewsError.setTextViewText(R.id.ketvirtas, "")
        viewsError.setTextViewText(R.id.penktas, "")
        appWidgetManager.updateAppWidget(appWidgetId, viewsError)
        return // Baigiame funkcijos vykdymą
    }

    // Jei kodas pasiekia šią vietą, jsonObject buvo sėkmingai sukurtas.
    // Toliau bandome išgauti duomenis iš JSON struktūros.
    try {
        val journeysArray = jsonObject.getJSONArray("Journeys")
        // Tikriname, ar journeysArray nėra tuščias
        if (journeysArray.length() == 0) {
            Log.d("WidgetDebug", "Journeys masyvas yra tuščias.")
            val viewsNoData = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
            // Bandome gauti stotelių pavadinimus iš pirminio JSON, jei įmanoma, klaidų pranešimui
            // Tai daroma atsargiai, nes raktai gali neegzistuoti
            var originName = "Nežinoma"
            var destName = "Nežinoma"
            try {
                // Pabandome ištraukti stotelių pavadinimus iš pirmo elemento, jei toks yra,
                // arba iš kitų galimų vietų, priklausomai nuo API atsako struktūros klaidų atveju.
                // Ši dalis yra spekuliatyvi ir priklauso nuo to, ką API grąžina, kai nėra kelionių.
                // Jei API grąžina tuščią "Journeys", bet turi kitur stotelių info, čia būtų vieta ją paimti.
                // Pavyzdžiui, jei API grąžintų kažką panašaus į:
                // { "OriginStop": {"Name": "Vilnius"}, "DestinationStop": {"Name": "Kaunas"}, "Journeys": [] }
                // if (jsonObject.has("OriginStop") && jsonObject.getJSONObject("OriginStop").has("Name")) {
                //     originName = jsonObject.getJSONObject("OriginStop").getString("Name")
                // }
                // if (jsonObject.has("DestinationStop") && jsonObject.getJSONObject("DestinationStop").has("Name")) {
                //     destName = jsonObject.getJSONObject("DestinationStop").getString("Name")
                // }
                // Jei tokios informacijos nėra, paliekame "Nežinoma"
            } catch (nameEx: JSONException) {
                Log.w("WidgetDebug", "Nepavyko gauti stotelių pavadinimų tuščiam Journeys masyvui: ${nameEx.message}")
            }
            viewsNoData.setTextViewText(R.id.Marsrutas1, "$originName ➞ $destName") // Rodome kryptį, jei pavyko gauti
            viewsNoData.setTextViewText(R.id.pirmas, "Šiandien reisų nėra")
            viewsNoData.setTextViewText(R.id.antras, "")
            viewsNoData.setTextViewText(R.id.trecias, "")
            viewsNoData.setTextViewText(R.id.ketvirtas, "")
            viewsNoData.setTextViewText(R.id.penktas, "")
            appWidgetManager.updateAppWidget(appWidgetId, viewsNoData)
            return
        }

        val laikai = Array(journeysArray.length().coerceAtMost(5)) { "" } // Imame ne daugiau kaip 5 laikus
        var kryptis = ""

        // Iteruojame per keliones, bet ne daugiau kaip 5 kartus
        for (i in 0 until journeysArray.length().coerceAtMost(5)) {
            val journey = journeysArray.getJSONObject(i) // Imame nuo pradžios (0, 1, 2...)

            // Saugus būdas gauti reikšmes, naudojant .optString() ar .optJSONObject()
            // .optString("Raktas", "NumatytojiReikšmėJeiNėraArbaNeString")
            val originStop = journey.optJSONObject("Origin")?.optJSONObject("Stop")
            val destinationStop = journey.optJSONObject("Destination")?.optJSONObject("Stop")

            val originCity = originStop?.optString("Name", "N/A") ?: "N/A"
            val destinationCity = destinationStop?.optString("Name", "N/A") ?: "N/A"

            // Nustatome kryptį tik vieną kartą (iš pirmos kelionės)
            if (i == 0) {
                kryptis = "$originCity ➞ $destinationCity"
            }

            val actualDepartureDateTime = journey.optJSONObject("Origin")?.optString("ActualDepartureDateTime", "") ?: ""
            val plannedArrivalDateTime = journey.optJSONObject("Destination")?.optString("PlannedArrivalDateTime", "") ?: ""

            var departureTime = "N/A"
            if (actualDepartureDateTime.contains("T") && actualDepartureDateTime.length > actualDepartureDateTime.indexOf("T") + 5) {
                departureTime = actualDepartureDateTime.split("T")[1].substring(0, 5) // HH:mm
            }

            var arrivalTime = "N/A"
            if (plannedArrivalDateTime.contains("T") && plannedArrivalDateTime.length > plannedArrivalDateTime.indexOf("T") + 5) {
                arrivalTime = plannedArrivalDateTime.split("T")[1].substring(0, 5) // HH:mm
            }

            laikai[i] = "$departureTime - $arrivalTime"
        }

        val views = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
        views.setTextViewText(R.id.Marsrutas1, kryptis)
        views.setTextViewText(R.id.pirmas, laikai.getOrElse(0) { "" })
        views.setTextViewText(R.id.antras, laikai.getOrElse(1) { "" })
        views.setTextViewText(R.id.trecias, laikai.getOrElse(2) { "" })
        views.setTextViewText(R.id.ketvirtas, laikai.getOrElse(3) { "" })
        views.setTextViewText(R.id.penktas, laikai.getOrElse(4) { "" })
        appWidgetManager.updateAppWidget(appWidgetId, views)

    } catch (e: JSONException) {
        // Jei įvyksta JSONException apdorojant JSON struktūrą (pvz., trūksta rakto)
        Log.e("WidgetDebug", "JSONException apdorojant JSON struktūrą: ${e.message}")
        val viewsError = RemoteViews(context.packageName, R.layout.traukinio_grafikas)
        viewsError.setTextViewText(R.id.Marsrutas1, "Klaida: Duomenų struktūra")
        viewsError.setTextViewText(R.id.pirmas, "")
        viewsError.setTextViewText(R.id.antras, "")
        viewsError.setTextViewText(R.id.trecias, "")
        viewsError.setTextViewText(R.id.ketvirtas, "")
        viewsError.setTextViewText(R.id.penktas, "")
        appWidgetManager.updateAppWidget(appWidgetId, viewsError)
    }
}
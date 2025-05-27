package com.example.traukiniuwiget

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.traukiniuwiget.ui.theme.TraukiniuWigetTheme

// --- Duomenų struktūra ir sąrašas ---
data class Vietove(val id: Int, val pavadinimas: String)

val miestuSarasas = listOf(
    Vietove(16, "Kaunas"),
    Vietove(17, "Vilnius"),
    Vietove(11, "Kaišiadorys"),
    Vietove(15, "Palemonas"),

    Vietove(1, "Paneriai"),
    Vietove(2, "Vokė"),
    Vietove(3, "Lentvaris"),
    Vietove(4, "Kariotiškės"),
    Vietove(5, "Rykantai"),
    Vietove(6, "Lazdėnai"),
    Vietove(7, "Baltamiškis"),
    Vietove(8, "Vievis"),
    Vietove(9, "Kaugonys"),
    Vietove(10, "Žasliai"),
    Vietove(12, "Pamieris"),
    Vietove(13, "Pravieniškės"),
    Vietove(14, "Karčiupis"),


)

// --- SharedPreferences konstantos (svarbu, kad sutaptų su widget) ---
//const val PREFS_NAME = "TraukiniuWidgetPrefs" // Įsitikinkite, kad šis pavadinimas teisingas
//const val KEY_CHOICE1 = "widget_choice1_id"   // Įsitikinkite, kad šis raktas teisingas
//const val KEY_CHOICE2 = "widget_choice2_id"   // Įsitikinkite, kad šis raktas teisingas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pradiniai SharedPreferences nustatymai onCreate nebereikalingi,
        // nes reikšmės bus nustatomos mygtukų paspaudimais.
        // Galite čia įkelti esamas reikšmes, jei norite jas rodyti UI.

        setContent {
            TraukiniuWigetTheme {
                // Naudojame LocalContext, kad gautume prieigą prie SharedPreferences Composable funkcijose
                val context = LocalContext.current

                // Būsenos kintamieji, kad matytume pasirinktus ID (nebūtina, bet naudinga demonstracijai)
                val pasirinktasId1 = remember {
                    val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    mutableStateOf(sharedPref.getInt(KEY_CHOICE1, -1)) // -1 kaip numatytoji reikšmė, jei nieko nėra
                }
                val pasirinktasId2 = remember {
                    val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    mutableStateOf(sharedPref.getInt(KEY_CHOICE2, -1))
                }


                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp) // Pridedame šiek tiek vidinio tarpo aplink turinį
                            .verticalScroll(rememberScrollState()) // Leidžia slinkti, jei turinys netelpa
                    ) {
                        // --- Pirmas Miestas ---
                        Text("Pasirinkite pirmą miestą:", style = MaterialTheme.typography.headlineSmall)
                        // Rodome šiuo metu pasirinktą ID (jei yra)
                        if (pasirinktasId1.value != -1) {
                            val miestas = miestuSarasas.find { it.id == pasirinktasId1.value }?.pavadinimas ?: "Nežinomas"
                            Text("Dabartinis pasirinkimas 1: $miestas (ID: ${pasirinktasId1.value})")
                        }
                        miestuSarasas.forEach { vietove ->
                            Button(onClick = {
                                val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                with(sharedPref.edit()) {
                                    putInt(KEY_CHOICE1, vietove.id)
                                    apply()
                                }
                                pasirinktasId1.value = vietove.id // Atnaujiname būseną UI
                                println("Pirmas miestas nustatytas į: ${vietove.pavadinimas} (ID: ${vietove.id})")
                            }) {
                                Text(vietove.pavadinimas)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp)) // Tarpas tarp sekcijų

                        // --- Antras Miestas ---
                        Text("Pasirinkite antrą miestą:", style = MaterialTheme.typography.headlineSmall)
                        // Rodome šiuo metu pasirinktą ID (jei yra)
                        if (pasirinktasId2.value != -1) {
                            val miestas = miestuSarasas.find { it.id == pasirinktasId2.value }?.pavadinimas ?: "Nežinomas"
                            Text("Dabartinis pasirinkimas 2: $miestas (ID: ${pasirinktasId2.value})")
                        }
                        miestuSarasas.forEach { vietove ->
                            Button(onClick = {
                                val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                with(sharedPref.edit()) {
                                    putInt(KEY_CHOICE2, vietove.id)
                                    apply()
                                }
                                pasirinktasId2.value = vietove.id // Atnaujiname būseną UI
                                println("Antras miestas nustatytas į: ${vietove.pavadinimas} (ID: ${vietove.id})")
                            }) {
                                Text(vietove.pavadinimas)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Greeting funkcija gali likti, jei ją naudosite kitur, arba galite ją pašalinti.
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TraukiniuWigetTheme {
        // Galite patobulinti peržiūrą, kad rodytų pagrindinį turinį
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Pasirinkite pirmą miestą:", style = MaterialTheme.typography.headlineSmall)
            miestuSarasas.take(2).forEach { Button(onClick = {}) { Text(it.pavadinimas) } }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Pasirinkite antrą miestą:", style = MaterialTheme.typography.headlineSmall)
            miestuSarasas.take(2).forEach { Button(onClick = {}) { Text(it.pavadinimas) } }
        }
    }
}
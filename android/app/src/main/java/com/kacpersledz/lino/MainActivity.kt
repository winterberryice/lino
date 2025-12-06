package com.kacpersledz.lino

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.kacpersledz.lino.navigation.NavGraph
import com.kacpersledz.lino.ui.theme.LinoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinoTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
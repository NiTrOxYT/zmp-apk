package com.sitelink.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.sitelink.manager.ui.navigation.SiteLinkNavGraph
import com.sitelink.manager.ui.theme.SiteLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SiteLinkApp

        setContent {
            SiteLinkTheme {
                val navController = rememberNavController()
                SiteLinkNavGraph(navController = navController, app = app)
            }
        }
    }
}

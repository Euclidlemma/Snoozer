package com.example.snoozer.report

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.snoozer.Route.Routes

/** The weekly report, wired to [ReportViewModel]. Opened from the week screen's strip. */
@Composable
fun WeeklyReportScreen(navController: NavController, viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold { padding ->
        ReportContent(
            state = state,
            // Pops only this screen, so a double tap on Back can't also leave the week screen.
            onBack = { navController.popBackStack(Routes.WeeklyReport.route, inclusive = true) },
            modifier = Modifier.padding(padding),
        )
    }
}

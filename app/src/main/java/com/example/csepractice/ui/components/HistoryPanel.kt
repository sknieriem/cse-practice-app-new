package com.example.csepractice.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.csepractice.R
import com.example.csepractice.data.PracticeSession
import com.example.csepractice.viewmodel.PracticeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable history view shown both from the menu and after a session finishes.
 * Replaces ~75 lines of duplicated layout previously in MainActivity.
 */
@Composable
fun HistoryPanel(
    viewModel: PracticeViewModel,
    onViewChartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.progress_history),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.no_history),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            SessionTableHeader()
            sessions.forEach { SessionRow(it) }
        }

        Spacer(Modifier.height(16.dp))

        val averageScore = if (sessions.isNotEmpty()) {
            sessions.map { it.score }.average().toInt()
        } else 0

        Text(
            text = stringResource(R.string.average_score, averageScore),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.category_averages),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        categories.forEach { cat ->
            val avg by viewModel.getAverageForCategory(cat).collectAsState(initial = null)
            Text(
                text = stringResource(
                    R.string.category_average_row,
                    cat,
                    (avg ?: 0.0).toInt()
                )
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onViewChartClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.view_progress_chart))
        }
    }
}

@Composable
private fun SessionTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(stringResource(R.string.col_date), modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.col_score), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.col_time), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SessionRow(session: PracticeSession) {
    val formattedDate = remember(session.date) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(session.date))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formattedDate, modifier = Modifier.weight(2f))
        Text("${session.score}%", modifier = Modifier.weight(1f))
        Text("${session.timeTaken / 1000}s", modifier = Modifier.weight(1f))
    }
}

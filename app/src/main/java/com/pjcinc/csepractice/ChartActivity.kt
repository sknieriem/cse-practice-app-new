package com.pjcinc.csepractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.pjcinc.csepractice.data.PracticeSession
import com.pjcinc.csepractice.repository.QuestionRepository
import com.pjcinc.csepractice.ui.theme.CSEPracticeAppTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.flow.Flow

class ChartActivity : ComponentActivity() {
    private val repository by lazy { QuestionRepository(applicationContext) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CSEPracticeAppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.chart_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back)
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    ScoreChartScreen(
                        modifier = Modifier.padding(innerPadding),
                        sessions = repository.getAllSessions()
                    )
                }
            }
        }
    }
}

@Composable
fun ScoreChartScreen(
    modifier: Modifier = Modifier,
    sessions: Flow<List<PracticeSession>>
) {
    val sessionList by sessions.collectAsState(emptyList())
    val description = stringResource(R.string.chart_description)
    val datasetLabel = stringResource(R.string.chart_dataset_label)

    // Pull theme colors so the chart respects light/dark mode.
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            LineChart(ctx).apply {
                this.description.text = description
                this.description.textColor = onSurfaceColor
                setTouchEnabled(true)
                setDragEnabled(true)
                setScaleEnabled(true)
                setPinchZoom(true)
                legend.textColor = onSurfaceColor
                xAxis.textColor = onSurfaceColor
                axisLeft.textColor = onSurfaceColor
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            // Oldest -> newest left-to-right (sessions come in DESC order from DAO).
            val entries = sessionList.reversed().mapIndexed { index, session ->
                Entry(index.toFloat(), session.score.toFloat())
            }
            val dataSet = LineDataSet(entries, datasetLabel).apply {
                color = primaryColor
                valueTextColor = onSurfaceColor
                lineWidth = 2f
                circleRadius = 4f
                setCircleColor(primaryColor)
            }
            chart.data = if (entries.isEmpty()) null else LineData(dataSet)
            chart.invalidate()
        }
    )
}

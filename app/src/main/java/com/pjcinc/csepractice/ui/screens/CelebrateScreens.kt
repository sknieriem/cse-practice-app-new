package com.pjcinc.csepractice.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pjcinc.csepractice.domain.Encourage
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.SessionResult
import com.pjcinc.csepractice.ui.share.ShareCards
import com.pjcinc.csepractice.ui.share.SharePreview
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pjcinc.csepractice.ui.theme.Blue
import com.pjcinc.csepractice.ui.theme.Navy
import com.pjcinc.csepractice.ui.theme.Sun
import com.pjcinc.csepractice.viewmodel.AppViewModel
import com.pjcinc.csepractice.viewmodel.Overlay
import kotlin.random.Random

/** First time an exam-mode score reaches the passing rating. */
@Composable
fun PasadoScreen(vm: AppViewModel, r: SessionResult) {
    val user by vm.user.collectAsState()
    val lang = user?.language ?: "en"
    val card = ShareCards.pasadoMock(r.percent, user?.firstName ?: "", lang)
    CelebrationLayout(
        emoji = "🎉",
        title = Encourage.t(lang, "PASSING LEVEL!", "PASADO LEVEL!"),
        body = Encourage.t(lang,
            "You scored ${r.percent}% on a timed mock exam. The passing rating is ${Planner.PASS_MARK}%. This is what all your practice built. Tell the people cheering for you!",
            "${r.percent}% ang nakuha mo sa timed mock exam. Ang pasado ay ${Planner.PASS_MARK}%. Bunga 'yan ng lahat ng pag-aaral mo. Ibalita mo sa pamilya mo!"),
        card = card,
        shareLabel = Encourage.t(lang, "Share with family 📤", "I-share sa pamilya 📤"),
        onShare = { f -> vm.shareNow(card, "pasado", f) },
        continueLabel = Encourage.t(lang, "See my results", "Tingnan ang resulta"),
        onContinue = { vm.onBack() }
    )
}

/** The user reported passing the REAL exam. */
@Composable
fun PassedExamScreen(vm: AppViewModel, level: String) {
    val user by vm.user.collectAsState()
    val lang = user?.language ?: "en"
    val name = user?.firstName ?: ""
    val card = ShareCards.passedExam(level, name, lang)
    CelebrationLayout(
        emoji = "🎓",
        title = Encourage.t(lang, "PASADO! Congratulations${if (name.isNotBlank()) ", $name" else ""}!", "PASADO! Congrats${if (name.isNotBlank()) ", $name" else ""}!"),
        body = Encourage.t(lang,
            "You passed the Civil Service Exam ($level). All those days of practice paid off. This one is yours — celebrate it with everyone who believed in you.",
            "Pumasa ka sa Civil Service Exam ($level)! Nagbunga ang lahat ng pagod at pag-aaral mo. Sa'yo 'to — i-celebrate kasama ang lahat ng naniwala sa'yo."),
        card = card,
        shareLabel = Encourage.t(lang, "Share the good news 📤", "Ibalita sa lahat 📤"),
        onShare = { f -> vm.shareNow(card, "passed", f) },
        continueLabel = Encourage.t(lang, "Done", "Tapos"),
        onContinue = { vm.onBack() }
    )
}

@Composable
private fun CelebrationLayout(
    emoji: String,
    title: String,
    body: String,
    card: ShareCards.Card,
    shareLabel: String,
    onShare: (ShareCards.Format) -> Unit,
    continueLabel: String,
    onContinue: () -> Unit
) {
    var format by rememberSaveable { mutableStateOf(ShareCards.Format.SQUARE) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, Blue)))) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 72.sp)
            Text(title, color = Sun, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(body, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            SharePreview(card, format, { format = it }, card.lang, Modifier.fillMaxWidth(), onDark = true)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onShare(format) },
                colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Navy),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(shareLabel, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(continueLabel, color = Color.White)
            }
        }
    }
}

/** Light confetti: ~60 falling paper bits. Pure drawing, no assets. */
@Composable
private fun Confetti(modifier: Modifier) {
    val pieces = remember {
        val rnd = Random(7)
        val colors = listOf(Sun, Color(0xFFE5484D), Color.White, Color(0xFF7BE3A6), Color(0xFFB4C8FF))
        List(60) { Triple(rnd.nextFloat(), rnd.nextFloat(), colors[rnd.nextInt(colors.size)]) to rnd.nextFloat() * 360f }
    }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "fall"
    )
    Canvas(modifier) {
        pieces.forEach { (p, angle) ->
            val (x, y0, color) = p
            val y = ((y0 + t) % 1f) * size.height
            val cx = x * size.width
            rotate(angle + t * 360f, Offset(cx, y)) {
                drawRect(color.copy(alpha = 0.85f), topLeft = Offset(cx, y), size = Size(14f, 22f))
            }
        }
    }
}

fun isCelebration(o: Overlay) = o is Overlay.Pasado || o is Overlay.PassedExam

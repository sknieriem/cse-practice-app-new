package com.pjcinc.csepractice.ui.screens

import com.pjcinc.csepractice.ui.components.NoWrapChip
import com.pjcinc.csepractice.ui.components.ChipFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.domain.SessionResult
import com.pjcinc.csepractice.ui.components.Bar
import com.pjcinc.csepractice.ui.components.Pill
import com.pjcinc.csepractice.ui.components.SectionTitle
import com.pjcinc.csepractice.ui.components.formatClock
import com.pjcinc.csepractice.ui.components.subjectStyle
import com.pjcinc.csepractice.ui.theme.Blue
import com.pjcinc.csepractice.ui.theme.Flame
import com.pjcinc.csepractice.ui.theme.LocalFeedback
import com.pjcinc.csepractice.ui.theme.Navy
import com.pjcinc.csepractice.ui.theme.Sun
import com.pjcinc.csepractice.domain.Encourage
import com.pjcinc.csepractice.ui.components.CelebrationCard
import com.pjcinc.csepractice.ui.components.InspirationCard
import com.pjcinc.csepractice.ui.share.ShareCards
import com.pjcinc.csepractice.ui.share.ShareLabels
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.testTag
import com.pjcinc.csepractice.viewmodel.AppViewModel

@Composable
fun ResultScreen(vm: AppViewModel, r: SessionResult) {
    val user by vm.user.collectAsState()
    val wrong = r.items.count { !it.isCorrect }
    val pass = r.percent >= Game.PASS_PERCENT
    val lang = user?.language ?: "en"
    val firstName = user?.firstName ?: ""
    val anim = remember { Animatable(0f) }
    LaunchedEffect(r) { anim.animateTo(r.percent / 100f, tween(900)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Navy, Blue)))
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.title, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { anim.value },
                        modifier = Modifier.size(150.dp),
                        color = if (pass) Sun else Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 12.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(anim.value * 100).toInt()}%", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${r.correct} / ${r.total} correct", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(Encourage.cheer(lang, r.percent), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (ShareCards.isPerfect(r.correct, r.total)) {
                    Button(
                        onClick = { vm.share(ShareCards.perfect(r.total, firstName, lang), "perfect") },
                        colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Navy),
                        modifier = Modifier.padding(top = 8.dp).testTag("share_perfect")
                    ) { Text(ShareLabels.perfect(lang), textAlign = TextAlign.Center) }
                }
                Text(
                    "Time ${formatClock(r.timeMs / 1000)} · pass mark ${Game.PASS_PERCENT}%",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            // ---- rewards ----
            val reward = r.reward
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("+${reward.xpGained} XP", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                        if (reward.streakExtended) Pill("🔥 ${reward.newState.streak}-day streak", Flame)
                    }
                    reward.breakdown.forEach { (label, xp) ->
                        Row {
                            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("+$xp", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    val lp = Game.progress(reward.newState.xp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (reward.leveledUp) "🎉 Level up! You're now Lvl ${lp.level} · ${lp.title}" else "Lvl ${lp.level} · ${lp.title}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Bar(lp.fraction, MaterialTheme.colorScheme.secondary)
                    if (reward.leveledUp) {
                        OutlinedButton(
                            onClick = { vm.share(ShareCards.levelUp(lp.level, lp.title, firstName, lang), "level_up") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("share_level_up")
                        ) { Text(ShareLabels.levelUp(lang), textAlign = TextAlign.Center) }
                    }
                    val streakDays = reward.newState.streak
                    if (reward.streakExtended && streakDays in ShareCards.STREAK_MILESTONES) {
                        OutlinedButton(
                            onClick = { vm.share(ShareCards.streak(streakDays, firstName, lang), "streak_$streakDays") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("share_streak")
                        ) { Text(ShareLabels.streak(lang, streakDays), textAlign = TextAlign.Center) }
                    }
                }
            }

            // ---- what went well: specific and true ----
            if (r.praise.isNotEmpty()) {
                SectionTitle(Encourage.t(lang, "What went well", "Mga nagawa mong maganda"))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        r.praise.forEach { line ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("✓ ", color = LocalFeedback.current.good, fontWeight = FontWeight.Bold)
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (r.celebrations.isNotEmpty()) {
                SectionTitle(Encourage.t(lang, "Milestones", "Mga milestone"))
                r.celebrations.forEach { c ->
                    CelebrationCard(c, Modifier.padding(vertical = 4.dp))
                    if (c.id == "first_mock") {
                        OutlinedButton(
                            onClick = { vm.share(ShareCards.firstMock(firstName, lang), "first_mock") },
                            modifier = Modifier.fillMaxWidth().testTag("share_first_mock")
                        ) { Text(ShareLabels.firstMock(lang), textAlign = TextAlign.Center) }
                    }
                }
            }
            // hard day? remind them who they're doing this for
            if (r.percent < 60) {
                user?.let { u -> InspirationCard(u.inspirationLine, u.inspirationPhoto, lang, Modifier.padding(top = 12.dp)) }
            }

            if (reward.newBadges.isNotEmpty()) {
                SectionTitle("New badge${if (reward.newBadges.size > 1) "s" else ""}!")
                reward.newBadges.forEach { b ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(b.emoji, fontSize = 30.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(b.title, style = MaterialTheme.typography.titleMedium)
                                Text(b.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(
                            onClick = { vm.share(ShareCards.badge(b, firstName, lang), "badge_${b.id}") },
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).testTag("share_badge_${b.id}")
                        ) { Text(ShareLabels.badge(lang)) }
                    }
                }
            }

            if (r.categories.size > 1 || r.categories.firstOrNull()?.let { it.total > 0 } == true) {
                SectionTitle("By subject")
                r.categories.forEach { c ->
                    val st = subjectStyle(c.category)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(st.short, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                        Bar(if (c.total == 0) 0f else c.correct.toFloat() / c.total, st.color, Modifier.weight(1f), height = 10.dp)
                        Text("${c.correct}/${c.total}", modifier = Modifier.width(56.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            if (wrong > 0) {
                Button(onClick = { vm.openReview(r, onlyWrong = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Review my $wrong mistake${if (wrong == 1) "" else "s"}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.retryMistakes(r) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try the mistakes again")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { vm.openReview(r, onlyWrong = false) }, modifier = Modifier.fillMaxWidth()) {
                Text("See all answers")
            }
            OutlinedButton(
                onClick = {
                    val u = user ?: return@OutlinedButton
                    val rd = r.readiness
                    vm.share(ShareCards.progress(u.firstName, rd?.takeIf { it.shown }?.overall, r.weeklyGain,
                        r.reward.newState.streak, Game.titleFor(Game.levelFor(r.reward.newState.xp)), rd?.answered ?: 0, lang), "progress")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(Encourage.t(lang, "Share my progress 📤", "I-share ang progress ko 📤")) }
            TextButton(onClick = { vm.closeResult() }, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Text("Done")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun cheer(p: Int) = when {
    p == 100 -> "Perfect! Ang galing mo! 🏆"
    p >= 80 -> "Passing level — keep it up! 💪"
    p >= 60 -> "Getting there. Review your mistakes! 📚"
    else -> "Every mistake is a lesson. Kaya mo 'yan! 🙌"
}

@Composable
fun ReviewScreen(vm: AppViewModel, r: SessionResult, onlyWrongInitially: Boolean) {
    val user by vm.user.collectAsState()
    var onlyWrong by rememberSaveable { mutableStateOf(onlyWrongInitially) }
    var tl by rememberSaveable { mutableStateOf(user?.language == "tl") }
    val fb = LocalFeedback.current
    val items = r.items.withIndex().filter { !onlyWrong || !it.value.isCorrect }
        .sortedByDescending { it.value.marked }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { vm.onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to results") }
            Text("Review answers", style = MaterialTheme.typography.titleLarge)
        }
        ChipFlow(Modifier.padding(horizontal = 16.dp)) {
            NoWrapChip("Mistakes", onlyWrong) { onlyWrong = true }
            NoWrapChip("All", !onlyWrong) { onlyWrong = false }
            NoWrapChip("Taglish", tl) { tl = !tl; vm.setLanguage(if (tl) "tl" else "en") }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (items.isEmpty()) item {
                Text("No mistakes — perfect score! 🎉", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(items, key = { _, it -> it.index }) { _, iv ->
                val it = iv.value
                val q = it.question
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Q${iv.index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Pill(subjectStyle(q.category).short, subjectStyle(q.category).color)
                            if (it.marked) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.Bookmark, contentDescription = "Marked", tint = Sun, modifier = Modifier.size(18.dp))
                            }
                        }
                        q.passage?.let { p ->
                            Text(p.take(160) + if (p.length > 160) "…" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(q.text, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val yourText = if (it.chosen < 0) "(skipped)" else q.options()[it.chosen]
                        AnswerLine("Your answer", yourText, if (it.isCorrect) fb.good else fb.bad)
                        if (!it.isCorrect) AnswerLine("Correct answer", q.options()[q.correctIndex()], fb.good)
                        ExplanationBody(vm, q, it.chosen, tl)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
private fun AnswerLine(label: String, value: String, color: Color) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

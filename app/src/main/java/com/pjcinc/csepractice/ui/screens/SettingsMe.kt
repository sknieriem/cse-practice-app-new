package com.pjcinc.csepractice.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.data.UserState
import com.pjcinc.csepractice.domain.Encourage
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.ui.components.rememberInspirationBitmap
import com.pjcinc.csepractice.viewmodel.AppViewModel

/** Name for share cards, "who I'm doing this for", streak freezes. All stays on the phone. */
@Composable
fun MeCard(vm: AppViewModel, user: UserState) {
    val lang = user.language
    var name by rememberSaveable { mutableStateOf(user.firstName) }
    var line by rememberSaveable { mutableStateOf(user.inspirationLine) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setInspirationPhoto(uri)
    }
    val bmp = rememberInspirationBitmap(user.inspirationPhoto)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30); vm.setFirstName(name) },
                label = { Text(Encourage.t(lang, "First name (for your share cards)", "Pangalan (para sa share cards)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(Encourage.t(lang, "Who I'm doing this for 💛", "Para kanino ako lumalaban 💛"), style = MaterialTheme.typography.titleMedium)
            Text(
                Encourage.t(lang, "A photo and a line we'll show you on hard days.", "Isang litrato at mensahe na ipapakita namin sa mahihirap na araw."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = line,
                onValueChange = { line = it.take(120); vm.setInspirationLine(line) },
                placeholder = { Text(Encourage.t(lang, "e.g. For Mama and Papa", "hal. Para kina Mama at Papa")) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bmp != null) {
                    Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(10.dp))
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (bmp == null) Encourage.t(lang, "Add a photo", "Magdagdag ng litrato") else Encourage.t(lang, "Change", "Palitan"))
                }
                if (bmp != null) TextButton(onClick = { vm.setInspirationPhoto(null) }) { Text(Encourage.t(lang, "Remove", "Alisin")) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                Encourage.t(lang, "Streak freezes: ${user.freezes} of ${Game.MAX_FREEZES} ❄️", "Streak freezes: ${user.freezes} sa ${Game.MAX_FREEZES} ❄️"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                Encourage.t(lang, "Earn one for every 7-day streak. If you miss a day, a freeze keeps your streak safe automatically.",
                    "May isa ka bawat 7-day streak. Kapag na-miss mo ang isang araw, automatic na safe ang streak mo."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

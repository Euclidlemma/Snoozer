package com.example.snoozer.ui.doodle

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import com.example.snoozer.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.snoozer.ui.theme.SnoozerTheme

/** Gallery of every doodle building block, in light and dark, for Android Studio's Preview pane. */
@Preview(name = "Doodle kit - light", widthDp = 360)
@Preview(name = "Doodle kit - dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DoodleKitPreview() {
    SnoozerTheme {
        val colors = SnoozerTheme.colors
        Column(
            modifier = Modifier
                .paperBackground(MaterialTheme.colorScheme.background, colors.paperLine)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.highlighter(colors.highlighter)) {
                Text("Monday", style = MaterialTheme.typography.headlineMedium)
            }

            DoodleCard(colors = colors.work, onClick = {}, seed = 1, modifier = Modifier.fillMaxWidth()) {
                Text("09:00", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Text("~ 10:30", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            }
            DoodleCard(colors = colors.study, onClick = {}, seed = 2, modifier = Modifier.fillMaxWidth()) {
                Text("22:00", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Text("~ 07:00 +1", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DoodleChip(selected = true, onClick = {}, label = "Mon", seed = 3)
                DoodleChip(selected = false, onClick = {}, label = "Tue", seed = 4)
                DoodleChip(selected = true, onClick = {}, label = "Wed", seed = 5)
            }

            // Hand-drawn icons, tinted with the current text color
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    R.drawable.ic_doodle_back,
                    R.drawable.ic_doodle_add,
                    R.drawable.ic_doodle_delete,
                    R.drawable.ic_doodle_clock,
                    R.drawable.ic_doodle_expand,
                ).forEach { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(32.dp)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DoodleButton(onClick = {}, seed = 6) { Text("Save") }
                DoodleButton(
                    onClick = {},
                    seed = 7,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    hatchColor = MaterialTheme.colorScheme.error,
                ) { Text("Delete") }
                DoodleButton(onClick = {}, enabled = false, seed = 8) { Text("Off") }
            }
        }
    }
}

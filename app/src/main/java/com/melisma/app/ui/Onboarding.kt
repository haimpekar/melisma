package com.melisma.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melisma.app.R

/** Guide for the Spotify-only experiment: no notification permission is requested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeSheet(
    accent: Color,
    permissionGranted: Boolean,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14141A),
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The sheet is dark whatever the system theme is, so the artwork drawn for a
                // dark surface is named explicitly rather than left to a night qualifier the
                // app would ignore. Its black field blends into the sheet, leaving the mark.
                Image(
                    painter = painterResource(R.drawable.ic_brand),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(13.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Welcome to Melisma",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Word-by-word synchronized lyrics for Spotify. This experimental version " +
                    "does not request access to notifications from other apps.",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )

            Step(
                number = "1",
                title = "Turn on Spotify Device Broadcast Status",
                body = "Open Spotify → Settings and privacy → Playback → " +
                    "Device Broadcast Status, and turn it ON. Then play or change a song. " +
                    "Spotify sends the song title, artist and playback position to Melisma. " +
                    "No Spotify password, developer key or Android notification access is needed. " +
                    "If the setting is missing or Spotify does not broadcast, lyrics cannot " +
                    "synchronize in this experimental version.",
                accent = accent,
                done = permissionGranted,
            )

            Step(
                number = "2",
                title = "No token needed for Spotify",
                body = "Four lyrics sources can be queried without a Spotify token:\n\n" +
                    "•  AMLL TTML Database — community-timed word-by-word lyrics, made by " +
                    "hand and released to the public domain\n" +
                    "•  LRCLIB — open community database\n" +
                    "•  Musixmatch — word-by-word for most Western music\n" +
                    "•  NetEase — word-by-word for East Asian music, with hand-checked " +
                    "romanization and translation\n\nTranslations that come with the lyrics " +
                    "are shown as they are. Translating into a language of your own choosing " +
                    "is a separate setting, because it runs a model on the phone and has to " +
                    "download it first.\n\nYou can also import your own .lrc or .ttml file " +
                    "for any track, which always wins.",
                accent = accent,
            )

            Spacer(Modifier.height(8.dp))

            SecondaryButton("Open settings", accent, onOpenSettings)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                "Start listening",
                accent,
                onDismiss,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "You can read this again from Settings → About.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(
    number: String,
    title: String,
    body: String,
    accent: Color,
    done: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) accent.copy(alpha = 0.55f) else accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else number,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.Black.copy(alpha = 0.86f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

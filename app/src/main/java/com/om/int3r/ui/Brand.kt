package com.om.int3r.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.om.int3r.R

/** "INT3R" set tight and bold, with the 3 in the logo lime. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("INT")
            withStyle(SpanStyle(color = Palette.Brand)) { append("3") }
            append("R")
        },
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
        modifier = modifier,
    )
}

/** Logo, wordmark and version, for the bottom of Settings. */
@Composable
fun BrandFooter() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Column(
        Modifier.fillMaxWidth().appear(0),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(painterResource(R.drawable.ic_launcher), contentDescription = "INT3R logo", modifier = Modifier.size(44.dp))
        Wordmark()
        if (version.isNotEmpty()) {
            Text("Version $version", style = MaterialTheme.typography.labelSmall, color = Palette.Secondary)
        }
    }
}
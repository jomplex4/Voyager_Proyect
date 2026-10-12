package com.personal.voyager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.personal.voyager.R

/**
 * Splash, laid out from the reference screenshot (720 x 1600): everything is a fraction of the screen
 * so it looks the same on the target phone. Only the icon is VOYAGER.
 */
@Composable
fun SplashScreen() {
    BoxWithConstraints(Modifier.fillMaxSize().background(Pal.Black)) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current

        val tile = w * (232f / 720f)
        val tileCenterY = h * (776f / 1600f)

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = tileCenterY - tile / 2)
                .size(tile)
                .clip(RoundedCornerShape(tile * 0.25f))
                .background(Pal.IconBg),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.voyager_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(tile * (172f / 232f)),
            )
        }

        val fontSize = with(density) { (w * (21.4f / 720f)).toSp() }
        Text(
            text = "POWERED BY",
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = h * (1321f / 1600f) - w * (21.4f / 720f) / 2),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                fontSize = fontSize,
                lineHeight = fontSize,
                letterSpacing = 0.3.em,
                color = Pal.Gray,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )

        Image(
            painter = painterResource(R.drawable.dm_logo),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * (1356f / 1600f))
                .width(w * (250f / 720f)),
        )
    }
}

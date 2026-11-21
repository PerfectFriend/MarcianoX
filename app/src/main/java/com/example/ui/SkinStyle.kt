package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import java.lang.Math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class SkinStyle(
    val id: String,
    val title: String,
    val primaryColor: Color,
    val accentColor: Color,
    val backgroundColor: Color,
    val cardColor: Color,
    val textColor: Color
) {
    CYBERPUNK(
        id = "CYBERPUNK",
        title = "CyberPunk",
        primaryColor = Color(0xFF00FF66), // Cyber green
        accentColor = Color(0xFF00F0FF), // Cyber cyan
        backgroundColor = Color(0xFF0A0B0E), // Ultra dark onyx
        cardColor = Color(0xFF14161F),
        textColor = Color.White
    ),
    ALIEN_STARSHIP(
        id = "ALIEN_STARSHIP",
        title = "Alien Starship",
        primaryColor = Color(0xFF39FF14), // Neon green
        accentColor = Color(0xFF8D38C9), // Purple neon
        backgroundColor = Color(0xFF0B1411), // Dark bio-chamber
        cardColor = Color(0xFF13221C),
        textColor = Color(0xFFD0F0C0)
    ),
    DARK_NEBULA(
        id = "DARK_NEBULA",
        title = "Dark Nebula",
        primaryColor = Color(0xFF8A2BE2), // Violet
        accentColor = Color(0xFFFF007F), // Neon pink
        backgroundColor = Color(0xFF080112), // Deep purple-black
        cardColor = Color(0xFF190D2A),
        textColor = Color(0xFFF3E5F5)
    ),
    COSMOS(
        id = "COSMOS",
        title = "Cosmos",
        primaryColor = Color(0xFFFFD700), // Starry Gold
        accentColor = Color(0xFF1E90FF), // Dodger Blue
        backgroundColor = Color(0xFF030A1C), // Deep midnight blue
        cardColor = Color(0xFF0E1A35),
        textColor = Color.White
    ),
    GAUDI(
        id = "GAUDI",
        title = "Gaudi",
        primaryColor = Color(0xFFFF7F50), // Coral/Amber
        accentColor = Color(0xFF20B2AA), // Sea Teal
        backgroundColor = Color(0xFF2D1610), // Terracotta
        cardColor = Color(0xFF42231A),
        textColor = Color(0xFFFFF8DC)
    ),
    MATRIX(
        id = "MATRIX",
        title = "Matrix",
        primaryColor = Color(0xFF00FF00), // Terminal green
        accentColor = Color(0xFF003300), // Dark matrix green
        backgroundColor = Color(0xFF000000), // Black
        cardColor = Color(0xFF0D0D0D),
        textColor = Color(0xFF33FF33)
    ),
    VAN_GOGH(
        id = "VAN_GOGH",
        title = "Van Gogh",
        primaryColor = Color(0xFFF39C12), // Golden Amber
        accentColor = Color(0xFF2980B9), // Starry Blue
        backgroundColor = Color(0xFF13233c), // Indigo Canvas
        cardColor = Color(0xFF1F385C),
        textColor = Color(0xFFFEF9E7)
    ),
    JAMAICA_SUMMER(
        id = "JAMAICA_SUMMER",
        title = "Jamaica Summer",
        primaryColor = Color(0xFF2ECC71), // Vibrant Green
        accentColor = Color(0xFFE74C3C), // Sunshine Red
        backgroundColor = Color(0xFF1B261A), // Island Night
        cardColor = Color(0xFF2C3E29),
        textColor = Color(0xFFF1C40F) // Gold Yellow
    ),
    MARIJUANA_WEED(
        id = "MARIJUANA_WEED",
        title = "Marijuana Weed",
        primaryColor = Color(0xFF4E9258), // Leaf Green
        accentColor = Color(0xFF1E4D2B), // Forest Green
        backgroundColor = Color(0xFF0D1C0F), // Dark Herbal
        cardColor = Color(0xFF1A331D),
        textColor = Color(0xFF99C68E)
    ),
    GALAXY(
        id = "GALAXY",
        title = "Galaxy",
        primaryColor = Color(0xFFE0115F), // Ruby Pink
        accentColor = Color(0xFF9370DB), // Medium Purple
        backgroundColor = Color(0xFF04020D), // Outer Space
        cardColor = Color(0xFF150C2D),
        textColor = Color(0xFFF0E6EF)
    ),
    MICROCOSMOS(
        id = "MICROCOSMOS",
        title = "MicroCosmos",
        primaryColor = Color(0xFF40E0D0), // Turquoise
        accentColor = Color(0xFFFF69B4), // Hot Pink
        backgroundColor = Color(0xFF0E1A22), // Deep water
        cardColor = Color(0xFF1B2E3C),
        textColor = Color(0xFFE0F7FA)
    ),
    LIVE_CELL(
        id = "LIVE_CELL",
        title = "Live Cell",
        primaryColor = Color(0xFF50C878), // Emerald Green
        accentColor = Color(0xFF3B9C9C), // Biophosphorescent
        backgroundColor = Color(0xFF021712), // Cellular fluid
        cardColor = Color(0xFF092C23),
        textColor = Color(0xFFD1F2EB)
    )
}

@Composable
fun CyberHudPanel(
    skin: SkinStyle,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    borderColor: Color = skin.primaryColor.copy(alpha = 0.45f),
    borderWidth: Dp = 1.dp,
    backdropAlpha: Float = 0.80f,
    ornamentAlpha: Float = 0.24f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        skin.cardColor.copy(alpha = (backdropAlpha + 0.06f).coerceAtMost(0.95f)),
                        skin.backgroundColor.copy(alpha = (backdropAlpha + 0.08f).coerceAtMost(0.96f))
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderColor,
                        skin.accentColor.copy(alpha = (borderColor.alpha * 0.7f)),
                        borderColor
                    )
                ),
                shape = shape
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_cyber_ornament_frame),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .alpha(ornamentAlpha)
        )
        content()
    }
}

@Composable
fun ThemeBackground(
    skin: SkinStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "background_waves")

    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "anim_phase"
    )

    val matrixProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "matrix_rain"
    )

    val wallpaperRes = when (skin) {
        SkinStyle.CYBERPUNK, SkinStyle.MATRIX, SkinStyle.MARIJUANA_WEED -> R.drawable.img_wallpaper_matrix_cyber
        SkinStyle.ALIEN_STARSHIP, SkinStyle.GAUDI, SkinStyle.JAMAICA_SUMMER, SkinStyle.VAN_GOGH -> R.drawable.img_wallpaper_martian_citadel
        SkinStyle.DARK_NEBULA, SkinStyle.COSMOS, SkinStyle.GALAXY, SkinStyle.MICROCOSMOS, SkinStyle.LIVE_CELL -> R.drawable.img_wallpaper_nebula_bio
    }

    val wallpaperTint = when (skin) {
        SkinStyle.CYBERPUNK, SkinStyle.MATRIX -> null
        else -> ColorFilter.tint(skin.primaryColor.copy(alpha = 0.25f), BlendMode.SrcAtop)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(skin.backgroundColor)
    ) {
        // Layer 1: Raster Cyberpunk-Matrix-Martian Wallpaper
        Image(
            painter = painterResource(id = wallpaperRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = wallpaperTint,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.56f)
        )

        // Layer 2: Subtle Ornament Frame Vignette & Contrast Scrim
        Image(
            painter = painterResource(id = R.drawable.img_cyber_ornament_frame),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.16f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            skin.backgroundColor.copy(alpha = 0.58f),
                            skin.backgroundColor.copy(alpha = 0.36f),
                            skin.backgroundColor.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        // Layer 3: Animated Vector HUD Reticles & Matrix Telemetry Overlays
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // HUD Corner Brackets for tactical framing
            val bracketLen = 36f
            val margin = 20f
            val cornerColor = skin.primaryColor.copy(alpha = 0.35f)
            // Top-left
            drawLine(cornerColor, Offset(margin, margin), Offset(margin + bracketLen, margin), 2f)
            drawLine(cornerColor, Offset(margin, margin), Offset(margin, margin + bracketLen), 2f)
            // Top-right
            drawLine(cornerColor, Offset(width - margin, margin), Offset(width - margin - bracketLen, margin), 2f)
            drawLine(cornerColor, Offset(width - margin, margin), Offset(width - margin, margin + bracketLen), 2f)
            // Bottom-left
            drawLine(cornerColor, Offset(margin, height - margin), Offset(margin + bracketLen, height - margin), 2f)
            drawLine(cornerColor, Offset(margin, height - margin), Offset(margin, height - margin - bracketLen), 2f)
            // Bottom-right
            drawLine(cornerColor, Offset(width - margin, height - margin), Offset(width - margin - bracketLen, height - margin), 2f)
            drawLine(cornerColor, Offset(width - margin, height - margin), Offset(width - margin, height - margin - bracketLen), 2f)

            when (skin) {
                SkinStyle.CYBERPUNK -> {
                    val strokeColor = skin.accentColor.copy(alpha = 0.07f)
                    val gridSpacing = 72f
                    var x = 0f
                    while (x < width) {
                        drawLine(strokeColor, Offset(x, 0f), Offset(x, height), 1f)
                        x += gridSpacing
                    }
                    var y = 0f
                    while (y < height) {
                        drawLine(strokeColor, Offset(0f, y), Offset(width, y), 1f)
                        y += gridSpacing
                    }
                    // Moving scanline
                    val scanY = ((matrixProgress * 0.45f) % height)
                    drawLine(
                        color = skin.primaryColor.copy(alpha = 0.16f),
                        start = Offset(0f, scanY),
                        end = Offset(width, scanY),
                        strokeWidth = 2f
                    )
                }

                SkinStyle.ALIEN_STARSHIP -> {
                    val starshipColor = skin.primaryColor.copy(alpha = 0.08f)
                    val starshipPurple = skin.accentColor.copy(alpha = 0.10f)
                    drawCircle(starshipColor, 340f, Offset(width / 2f, height * 0.85f), style = Stroke(width = 2.5f))
                    drawCircle(starshipPurple, 190f, Offset(width / 2f, height * 0.85f), style = Stroke(width = 1.5f))
                    drawLine(starshipColor, Offset(width / 2f - 380f, height * 0.85f), Offset(width / 2f + 380f, height * 0.85f), 1f)
                    val hexPath = Path()
                    val cx = width * 0.15f
                    val cy = height * 0.15f
                    val r = 74f
                    for (i in 0..6) {
                        val angle = i * PI / 3.0
                        val px = cx + r * cos(angle).toFloat()
                        val py = cy + r * sin(angle).toFloat()
                        if (i == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
                    }
                    hexPath.close()
                    drawPath(hexPath, color = starshipColor, style = Stroke(width = 1.5f))
                }

                SkinStyle.DARK_NEBULA -> {
                    // Swirling abstract nebula dust using custom radial gradients
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(skin.primaryColor.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(width * 0.2f, height * 0.3f),
                            radius = 600f
                        ),
                        radius = 600f,
                        center = Offset(width * 0.2f, height * 0.3f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(skin.accentColor.copy(alpha = 0.1f), Color.Transparent),
                            center = Offset(width * 0.8f, height * 0.7f),
                            radius = 500f
                        ),
                        radius = 500f,
                        center = Offset(width * 0.8f, height * 0.7f)
                    )
                }

                SkinStyle.COSMOS -> {
                    // Glittering stars and nebula fields
                    val starsCount = 25
                    val rand = java.util.Random(42) // Constant seed for stable stars
                    for (i in 0 until starsCount) {
                        val sx = rand.nextFloat() * width
                        val sy = rand.nextFloat() * height
                        val sizeStar = rand.nextFloat() * 3f + 1f
                        val starAlpha = (sin(animationProgress + i) + 1.2f) / 2.2f
                        drawCircle(
                            color = skin.primaryColor.copy(alpha = starAlpha * 0.7f),
                            radius = sizeStar,
                            center = Offset(sx, sy)
                        )
                    }
                    // Cosmic orbit ring
                    drawCircle(
                        color = skin.accentColor.copy(alpha = 0.05f),
                        radius = 450f,
                        center = Offset(width * 0.5f, height * 0.4f),
                        style = Stroke(width = 2f)
                    )
                }

                SkinStyle.GAUDI -> {
                    // Catalan mosaic style curves and geometric tiles
                    val gaudiColor = skin.accentColor.copy(alpha = 0.08f)
                    val coralColor = skin.primaryColor.copy(alpha = 0.06f)
                    
                    // Curved flowing paths
                    val wavePath1 = Path()
                    wavePath1.moveTo(0f, height * 0.3f)
                    wavePath1.cubicTo(width * 0.3f, height * 0.1f, width * 0.7f, height * 0.5f, width, height * 0.3f)
                    drawPath(wavePath1, color = gaudiColor, style = Stroke(width = 3f))

                    val wavePath2 = Path()
                    wavePath2.moveTo(0f, height * 0.7f)
                    wavePath2.cubicTo(width * 0.25f, height * 0.85f, width * 0.75f, height * 0.55f, width, height * 0.75f)
                    drawPath(wavePath2, color = coralColor, style = Stroke(width = 2.5f))
                    
                    // Draw mini mosaic circles along curves
                    for (i in 0..10) {
                        val t = i / 10f
                        val cx = t * width
                        val cy = (height * 0.3f) + (sin(t * PI.toFloat() * 2f) * 100f)
                        drawCircle(
                            color = skin.primaryColor.copy(alpha = 0.12f),
                            radius = 8f,
                            center = Offset(cx, cy)
                        )
                    }
                }

                SkinStyle.MATRIX -> {
                    // Vertical terminal rain paths (simulated using light/dark dashes)
                    val terminalColor = skin.primaryColor.copy(alpha = 0.12f)
                    val cols = 15
                    for (i in 0 until cols) {
                        val colX = (width / cols) * i + (width / cols / 2)
                        val offsetSpeed = (matrixProgress * (1f + i % 3)) % height
                        for (dash in 0..8) {
                            val dashY = (offsetSpeed + dash * 70) % height
                            val sizeDash = 15f - dash
                            if (sizeDash > 0) {
                                drawRect(
                                    color = terminalColor.copy(alpha = (10 - dash) / 15f),
                                    topLeft = Offset(colX, dashY),
                                    size = Size(4f, sizeDash * 2f)
                                )
                            }
                        }
                    }
                }

                SkinStyle.VAN_GOGH -> {
                    // Starry sky swirls in starry blue and orange yellow
                    val swirlColor = skin.primaryColor.copy(alpha = 0.08f)
                    val indigoStroke = skin.accentColor.copy(alpha = 0.06f)
                    
                    // Glowing stars
                    val vgStars = listOf(
                        Offset(width * 0.2f, height * 0.15f),
                        Offset(width * 0.8f, height * 0.25f),
                        Offset(width * 0.4f, height * 0.6f)
                    )
                    vgStars.forEachIndexed { i, offset ->
                        val rScale = 20f + 10f * sin(animationProgress + i)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(skin.primaryColor.copy(alpha = 0.15f), Color.Transparent),
                                center = offset,
                                radius = rScale * 2f
                            ),
                            radius = rScale * 2f,
                            center = offset
                        )
                        drawCircle(
                            color = skin.textColor.copy(alpha = 0.4f),
                            radius = 4f,
                            center = offset
                        )
                    }

                    // Cypress tree organic wave
                    val treePath = Path()
                    treePath.moveTo(0f, height)
                    treePath.cubicTo(width * 0.15f, height * 0.75f, width * 0.05f, height * 0.45f, 0f, height * 0.35f)
                    drawPath(treePath, color = swirlColor, style = Stroke(width = 4f))
                }

                SkinStyle.JAMAICA_SUMMER -> {
                    // Golden sun rays and tropical warm circular contours
                    val redSun = skin.accentColor.copy(alpha = 0.07f)
                    val yellowRay = Color(0xFFF1C40F).copy(alpha = 0.05f)
                    
                    drawCircle(
                        color = redSun,
                        radius = 280f,
                        center = Offset(width, 0f)
                    )
                    
                    // Radiating vector lines
                    for (angle in 0..90 step 15) {
                        val rad = angle * PI / 180f
                        val ex = width - 800f * cos(rad).toFloat()
                        val ey = 800f * sin(rad).toFloat()
                        drawLine(
                            color = yellowRay,
                            start = Offset(width, 0f),
                            end = Offset(ex, ey),
                            strokeWidth = 3f
                        )
                    }
                }

                SkinStyle.MARIJUANA_WEED -> {
                    // Stylized leaf geometrical frames and green mesh lines
                    val leafGreen = skin.primaryColor.copy(alpha = 0.06f)
                    val outlineGreen = skin.accentColor.copy(alpha = 0.08f)
                    
                    // Draw a big vector leaf outline in center background
                    val centerLeafX = width * 0.5f
                    val centerLeafY = height * 0.4f
                    
                    // Draw stylized geometry
                    for (i in 0..7) {
                        val angle = (i * 45f) * PI / 180f
                        val leafEndX = centerLeafX + 250f * cos(angle).toFloat()
                        val leafEndY = centerLeafY + 250f * sin(angle).toFloat()
                        
                        drawLine(
                            color = leafGreen,
                            start = Offset(centerLeafX, centerLeafY),
                            end = Offset(leafEndX, leafEndY),
                            strokeWidth = 2f
                        )
                        drawCircle(
                            color = outlineGreen,
                            radius = 12f,
                            center = Offset(leafEndX, leafEndY),
                            style = Stroke(width = 1.5f)
                        )
                    }
                    drawCircle(
                        color = leafGreen,
                        radius = 120f,
                        center = Offset(centerLeafX, centerLeafY),
                        style = Stroke(width = 2f)
                    )
                }

                SkinStyle.GALAXY -> {
                    // Swirling spiral arm paths and glittering clusters
                    val purpleGalaxy = skin.accentColor.copy(alpha = 0.1f)
                    val pinkGalaxy = skin.primaryColor.copy(alpha = 0.08f)
                    
                    // Spiral vectors
                    val spiralPath = Path()
                    val centerSX = width * 0.5f
                    val centerSY = height * 0.5f
                    spiralPath.moveTo(centerSX, centerSY)
                    for (theta in 0..1080 step 10) {
                        val rad = theta * PI / 180f
                        val rVal = 0.25f * theta
                        val px = centerSX + rVal * cos(rad).toFloat()
                        val py = centerSY + rVal * sin(rad).toFloat()
                        spiralPath.lineTo(px, py)
                    }
                    drawPath(spiralPath, color = purpleGalaxy, style = Stroke(width = 1.5f))
                    
                    // Swirl stars
                    for (i in 1..20) {
                        val t = i * 40f
                        val rad = t * PI / 180f + animationProgress * 0.5f
                        val rVal = 0.3f * t
                        val px = centerSX + rVal * cos(rad).toFloat()
                        val py = centerSY + rVal * sin(rad).toFloat()
                        drawCircle(
                            color = pinkGalaxy,
                            radius = 4f,
                            center = Offset(px, py)
                        )
                    }
                }

                SkinStyle.MICROCOSMOS -> {
                    // Flowing cellular/atomic diagrams in blue and pink neon
                    val bioTurquoise = skin.primaryColor.copy(alpha = 0.08f)
                    val bioPink = skin.accentColor.copy(alpha = 0.07f)
                    
                    // Floating molecules
                    val cells = listOf(
                        Offset(width * 0.3f, height * 0.25f),
                        Offset(width * 0.7f, height * 0.45f),
                        Offset(width * 0.4f, height * 0.75f)
                    )
                    
                    cells.forEachIndexed { i, offset ->
                        val moveOffset = Offset(
                            offset.x + 20f * sin(animationProgress + i).toFloat(),
                            offset.y + 20f * cos(animationProgress + i).toFloat()
                        )
                        drawCircle(
                            color = bioTurquoise,
                            radius = 40f,
                            center = moveOffset,
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = bioPink,
                            radius = 15f,
                            center = moveOffset
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = 6f,
                            center = moveOffset + Offset(6f, -6f)
                        )
                    }
                }

                SkinStyle.LIVE_CELL -> {
                    // Pulsing cellular shapes with glowing green biophosphorescence
                    val pulseScale = 1.0f + 0.08f * sin(animationProgress)
                    val greenFluid = skin.primaryColor.copy(alpha = 0.05f)
                    val glowFluid = skin.accentColor.copy(alpha = 0.07f)
                    
                    // Giant cell membrane
                    val cellPath = Path()
                    val cx = width * 0.5f
                    val cy = height * 0.35f
                    val baseRadius = 240f * pulseScale
                    
                    for (angle in 0..360 step 15) {
                        val rad = angle * PI / 180f
                        // add wobbly cell contours
                        val wobble = 25f * sin(angle * 5f * PI / 180f + animationProgress).toFloat()
                        val r = baseRadius + wobble
                        val px = cx + r * cos(rad).toFloat()
                        val py = cy + r * sin(rad).toFloat()
                        if (angle == 0) cellPath.moveTo(px, py) else cellPath.lineTo(px, py)
                    }
                    cellPath.close()
                    drawPath(cellPath, color = greenFluid)
                    drawPath(cellPath, color = skin.primaryColor.copy(alpha = 0.15f), style = Stroke(width = 2.5f))
                    
                    // Nucleus
                    drawCircle(
                        color = glowFluid,
                        radius = 60f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
        content()
    }
}

package com.example.dreamnet

import androidx.appcompat.app.AppCompatActivity
import android.content.res.Configuration
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dreamnet.data.*
import com.example.dreamnet.ui.DreamViewModel
import com.example.dreamnet.ui.SearchState
import com.example.dreamnet.ui.memory.DiaryDetailScreen
import com.example.dreamnet.ui.memory.DiaryScreen
import com.example.dreamnet.ui.memory.DiaryViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.dreamnet.ads.AdManager

enum class Screen { MAIN, DIARY, DIARY_DETAIL }

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var searchSuccessCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AdManager.initialize(this)
        
        setContent {
            val viewModel: DreamViewModel = hiltViewModel()
            val diaryViewModel: DiaryViewModel = hiltViewModel()

            val context = LocalContext.current
            
            val locale = remember(viewModel.selectedLanguage) {
                val tag = when (viewModel.selectedLanguage) {
                    "English" -> "en"
                    "O'zbek" -> "uz"
                    else -> "ru"
                }
                Locale(tag)
            }

            val configuration = LocalConfiguration.current
            val updatedConfig = remember(locale) {
                Configuration(configuration).apply {
                    setLocale(locale)
                }
            }
            val localizedContext = remember(updatedConfig) {
                context.createConfigurationContext(updatedConfig)
            }

            SideEffect {
                android.util.Log.d("DREAM_LOCALE", "Selected: ${viewModel.selectedLanguage}")
            }

            var showSplash by rememberSaveable { mutableStateOf(true) }
            var currentScreen by remember { mutableStateOf(Screen.MAIN) }
            var selectedEntry by remember { mutableStateOf<DiaryEntryEntity?>(null) }
            
            LaunchedEffect(Unit) {
                viewModel.prepopulateDatabase()
            }

            LaunchedEffect(viewModel.interpretationResult) {
                if (viewModel.interpretationResult != null && !viewModel.isLoading) {
                    searchSuccessCount++
                    if (searchSuccessCount % 4 == 0 && AdManager.isInterstitialReady()) {
                        AdManager.showInterstitial(context as Activity)
                    }
                }
            }

            if (showSplash) {
                AuthenticMysticSplash(onFinished = { showSplash = false })
            } else {
                CompositionLocalProvider(
                    LocalContentColor provides Color.White,
                    LocalConfiguration provides updatedConfig,
                    LocalContext provides localizedContext
                ) {
                    key(viewModel.selectedLanguage) {
                        when (currentScreen) {
                            Screen.MAIN -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    DreamNetProApp(
                                        viewModel = viewModel,
                                        diaryViewModel = diaryViewModel,
                                        onOpenDiary = { currentScreen = Screen.DIARY }
                                    )
                                    Box(modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xFF1A1C1E))) {
                                        AdManager.BannerAdView()
                                    }
                                }
                            }
                            Screen.DIARY -> {
                                DiaryScreen(
                                    viewModel = diaryViewModel,
                                    onBack = { currentScreen = Screen.MAIN },
                                    onAddEntry = {
                                        selectedEntry = null
                                        currentScreen = Screen.DIARY_DETAIL
                                    },
                                    onEditEntry = { entry ->
                                        selectedEntry = entry
                                        currentScreen = Screen.DIARY_DETAIL
                                    }
                                )
                            }
                            Screen.DIARY_DETAIL -> {
                                DiaryDetailScreen(
                                    viewModel = diaryViewModel,
                                    entry = selectedEntry,
                                    onBack = { currentScreen = Screen.DIARY }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthenticMysticSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(500, easing = LinearOutSlowInEasing))
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val anim = progress.value
        
        Image(
            painter = painterResource(id = R.drawable.mystic_eye),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(modifier = Modifier.size(500.dp)) {
            val w = size.width
            val h = size.height
            val eyeCenter = Offset(w * 0.5f, h * 0.525f)

            val burstProg = anim.pow(2f)
            val flashRadius = w * 0.05f + (w * 1.8f * burstProg)
            
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White,
                    0.2f to Color.White.copy(alpha = 0.95f),
                    0.6f to Color(0xFFD0BCFF).copy(alpha = 0.8f),
                    1f to Color.Transparent,
                    center = eyeCenter,
                    radius = flashRadius
                ),
                radius = flashRadius,
                center = eyeCenter
            )

            val rayAlpha = (1f - anim).coerceIn(0f, 1f)
            repeat(60) { i ->
                val angle = i * 6f
                rotate(angle, eyeCenter) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f * rayAlpha),
                        start = eyeCenter,
                        end = Offset(eyeCenter.x, eyeCenter.y - w * 2f),
                        strokeWidth = 4.dp.toPx()
                    )
                }
            }
        }

        if (anim > 0.85f) {
            val finalFill = ((anim - 0.85f) * 6.6f).coerceIn(0f, 1f)
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = finalFill)))
        }
    }
}

@Composable
fun DreamNetProApp(
    viewModel: DreamViewModel,
    diaryViewModel: DiaryViewModel,
    onOpenDiary: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var showSupportDialog by remember { mutableStateOf(false) }
    var showThanksDialog by remember { mutableStateOf(false) }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            containerColor = Color(0xFF1A1C1E),
            title = {
                Text(
                    text = stringResource(R.string.support_title),
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.support_text),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSupportDialog = false
                        if (AdManager.isRewardedReady()) {
                            AdManager.showRewarded(context as Activity) {
                                showThanksDialog = true
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.support_error_unavailable), Toast.LENGTH_LONG).show()
                            AdManager.loadRewarded(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4E197)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.support_btn_confirm), color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text(stringResource(R.string.support_btn_later), color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showThanksDialog) {
        AlertDialog(
            onDismissRequest = { showThanksDialog = false },
            containerColor = Color(0xFF1A1C1E),
            title = {
                Text(
                    text = stringResource(R.string.support_thanks_title),
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.support_thanks_text),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showThanksDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD0BCFF))
                ) {
                    Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1C1E)).padding(16.dp).padding(bottom = 50.dp).verticalScroll(scrollState)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp)
        ) {
            IconButton(onClick = onOpenDiary) {
                Icon(Icons.Default.Book, contentDescription = stringResource(R.string.diary_title), tint = Color(0xFFD0BCFF))
            }
            Text(text = stringResource(R.string.title_describe_dream), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.width(48.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            viewModel.languages.forEach { lang ->
                FilterChip(
                    selected = viewModel.selectedLanguage == lang,
                    onClick = { viewModel.selectedLanguage = lang },
                    label = { Text(lang, color = if (viewModel.selectedLanguage == lang) Color.Black else Color.White) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFD0BCFF), containerColor = Color(0xFF2D2F31))
                )
            }
        }

        Spacer(Modifier.height(24.dp))

            Surface(
                onClick = { showSupportDialog = true },
                color = Color(0xFF2D2F31).copy(alpha = 0.6f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    1.dp, 
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFFD0BCFF).copy(alpha = 0.5f), Color(0xFF9147FF).copy(alpha = 0.5f)),
                    )
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.support_btn_main),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

        Spacer(Modifier.height(16.dp))

        TextField(
            value = viewModel.text,
            onValueChange = { viewModel.text = it },
            placeholder = { Text(stringResource(R.string.placeholder_describe_dream), color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onSearchClick(context); keyboardController?.hide(); focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Color(0xFF2D2F31), focusedContainerColor = Color(0xFF2D2F31), unfocusedTextColor = Color.White, focusedTextColor = Color.White)
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { 
                viewModel.onSearchClick(context)
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
        ) {
            if (viewModel.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    if (viewModel.searchState == SearchState.LOADING) {
                         Spacer(Modifier.width(12.dp))
                         Text("DreamNet AI анализирует...", color = Color.Black, fontSize = 14.sp)
                    }
                }
            }
            else Text(stringResource(R.string.button_analyze), color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        if (viewModel.errorResId != null) {
            Text(
                text = stringResource(viewModel.errorResId!!), 
                color = Color.Red, 
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (viewModel.interpretationResult != null) {
            LaunchedEffect(viewModel.interpretationResult) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Spacer(Modifier.height(24.dp))
            
            when (val result = viewModel.interpretationResult!!) {
                is InterpretationResult.Local -> {
                    Text(
                        text = result.result.entry.word.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // ГРАФА: СОННИКИ ПО АВТОРАМ
                    result.result.meanings.forEach { meaning ->
                        val source = viewModel.sources[meaning.sourceId]
                        InterpretationCard(
                            sourceName = source?.name ?: "Сонник",
                            meaning = meaning.meaning,
                            context = meaning.context,
                            isBase = meaning.sourceId == "dreamnet_base"
                        )
                    }

                    // AI АНАЛИЗ
                    if (!result.aiSummary.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        ResultCard(stringResource(R.string.label_ai_analysis), result.aiSummary!!, Icons.Default.AutoAwesome, Color(0xFFD0BCFF))
                    }

                    // ГРАФЫ: СДЕЛАЙТЕ / ИЗБЕГАЙТЕ
                    if (!result.adviceDo.isNullOrBlank() || !result.adviceDont.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!result.adviceDo.isNullOrBlank()) {
                                AdviceCard(stringResource(R.string.title_do), result.adviceDo!!, Icons.Default.CheckCircle, Color(0xFFB4E197), Modifier.weight(1f))
                            }
                            if (!result.adviceDont.isNullOrBlank()) {
                                AdviceCard(stringResource(R.string.title_avoid), result.adviceDont!!, Icons.Default.Warning, Color(0xFFFF8A8A), Modifier.weight(1f))
                            }
                        }
                    }

                    // ГРАФА: ОПЫТ ЛЮДЕЙ
                    if (!result.realStories.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        ExperienceCard(stringResource(R.string.title_real_stories), result.realStories!!, Icons.Default.HistoryEdu, Color(0xFFB4E197))
                    }
                }
                is InterpretationResult.AI -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFFD0BCFF), Color(0xFF9147FF))))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (result.isContextual) "✨ DreamNet AI — Анализ" else "✨ DreamNet AI — Поиск по сонникам",
                                    color = Color(0xFFD0BCFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            
                            // АВТОРСКИЕ СОННИКИ ОТ ИИ
                            if (result.authorMeanings.isNotEmpty()) {
                                result.authorMeanings.forEach { am ->
                                    Spacer(Modifier.height(12.dp))
                                    Text(am.author, color = Color(0xFFD0BCFF).copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(am.meaning, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp)
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(Modifier.height(8.dp))
                            
                            Text(
                                text = "ОБЩИЙ АНАЛИЗ:", 
                                color = Color.Gray, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 11.sp
                            )
                            Text(
                                text = result.interpretation, 
                                color = Color.White, 
                                fontSize = 15.sp, 
                                lineHeight = 22.sp
                            )
                            
                            if (!result.adviceDo.isNullOrBlank() || !result.adviceDont.isNullOrBlank()) {
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (!result.adviceDo.isNullOrBlank()) {
                                        AdviceCard(stringResource(R.string.title_do), result.adviceDo!!, Icons.Default.CheckCircle, Color(0xFFB4E197), Modifier.weight(1f))
                                    }
                                    if (!result.adviceDont.isNullOrBlank()) {
                                        AdviceCard(stringResource(R.string.title_avoid), result.adviceDont!!, Icons.Default.Warning, Color(0xFFFF8A8A), Modifier.weight(1f))
                                    }
                                }
                            }

                            if (!result.realStories.isNullOrBlank()) {
                                Spacer(Modifier.height(16.dp))
                                ExperienceCard(stringResource(R.string.title_real_stories), result.realStories!!, Icons.Default.HistoryEdu, Color(0xFFB4E197))
                            }
                        }
                    }
                }
                else -> {}
            }

            // Save to Diary Button
            Button(
                onClick = {
                    val mainMeaning = when (val res = viewModel.interpretationResult!!) {
                        is InterpretationResult.Local -> res.result.meanings.firstOrNull()?.meaning ?: ""
                        is InterpretationResult.AI -> res.interpretation
                        else -> ""
                    }
                    diaryViewModel.saveEntry(
                        title = if (viewModel.text.length > 30) viewModel.text.take(30) + "..." else viewModel.text,
                        content = viewModel.text,
                        date = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()),
                        aiInterpretation = mainMeaning
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Book, contentDescription = null, tint = Color(0xFFD0BCFF))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.diary_save_button), color = Color.White)
            }
        }
    }
}

@Composable
fun InterpretationCard(
    sourceName: String,
    meaning: String,
    context: String?,
    isBase: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31)),
        shape = RoundedCornerShape(16.dp),
        border = if (isBase) BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f)) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isBase) Icons.Default.Info else Icons.Default.MenuBook, 
                    contentDescription = null, 
                    tint = if (isBase) Color(0xFFD0BCFF) else Color.Gray, 
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = sourceName,
                    color = if (isBase) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            if (!context.isNullOrBlank()) {
                Text(
                    text = "Контекст: $context", 
                    color = Color.Gray, 
                    fontSize = 12.sp, 
                    modifier = Modifier.padding(top = 4.dp),
                    fontStyle = FontStyle.Italic
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = meaning, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun ExperienceCard(title: String, content: String, icon: ImageVector, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31)), 
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(content, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
fun ResultCard(title: String, content: String, icon: ImageVector, accentColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(content, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun AdviceCard(title: String, content: String, icon: ImageVector, accentColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(content, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp)
        }
    }
}

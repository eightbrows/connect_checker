package io.github.eightbrows.connect_checker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.eightbrows.connect_checker.AppSettings.ColorTarget
import io.github.eightbrows.connect_checker.ui.theme.ConnectCheckerTheme

class MainActivity : ComponentActivity() {

    // 保存された言語設定を画面全体に反映する
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConnectCheckerTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InstructionScreen()
                }
            }
        }
    }
}

private const val INFO_URL = "https://eightbrows.github.io/"
private const val LICENSE_URL = "https://github.com/eightbrows/connect_checker/blob/master/LICENSE"

// 選択中ボタンの背景（白文字が読める濃さの青）
private val SELECTED_BUTTON_COLOR = Color(0xFF1976D2)

// 未選択ボタンの枠（既定の outlineVariant はダーク時に見えにくいため outline を使う）
@Composable
private fun outlinedBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

// アクセント色（見出し・選択状態）。ダーク時は背景に対して読めるよう少し明るくする
@Composable
private fun accentColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF64B5F6) else Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val noDataText = stringResource(R.string.no_data)
    val noPermissionText = stringResource(R.string.no_permission)
    val accent = accentColor()
    val bodyTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 必要な権限の許可状態を保持
    var hasUsagePermission by remember { mutableStateOf(DataUsage.hasUsageAccess(context)) }

    val prefs = context.getSharedPreferences("NetworkCheckerPrefs", Context.MODE_PRIVATE)
    var startDayInput by remember { mutableIntStateOf(prefs.getInt("start_day", 1)) }
    var expanded by remember { mutableStateOf(false) }

    // データ使用量を保持する変数
    var mobileDataUsage by remember { mutableStateOf(noDataText) }

    // 画面が開いた時や、起算日が変わった時に自動計算する
    LaunchedEffect(hasUsagePermission, startDayInput) {
        mobileDataUsage = if (hasUsagePermission) {
            DataUsage.getMobileDataUsageText(context, startDayInput)
        } else {
            noPermissionText
        }
    }

    // ライフサイクルを監視し、アプリがフォアグラウンドに戻った際に権限状態を再チェックする
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = DataUsage.hasUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 1. 必要な権限の許可セクション
            SettingsCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.main_title_permission), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 使用状況へのアクセス権限
                    if (hasUsagePermission) {
                        Text(stringResource(R.string.main_usage_granted), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.main_usage_desc), color = bodyTextColor, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336), contentColor = Color.White)
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.main_btn_usage))
                        }
                    }
                }
            }

            // 2. 通信量計算の起算日設定セクション
            SettingsCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.main_title_start_day), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.main_start_day_desc), color = bodyTextColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // ここからが「真っ二つUI（Row）」
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), // 左右のスキマ
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 【左半分】重さ(weight) 1f で起算日ドロップダウン
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                                OutlinedTextField(
                                    value = stringResource(R.string.main_day_format, startDayInput),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.main_label_start_day)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    (1..31).forEach { day ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.main_day_format, day)) },
                                            onClick = {
                                                startDayInput = day
                                                expanded = false
                                                prefs.edit { putInt("start_day", day) }
                                                AppSettings.requestWidgetUpdate(context)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 【右半分】重さ(weight) 1f で通信量を表示
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.main_current_usage),
                                fontSize = 14.sp,
                                color = bodyTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mobileDataUsage,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50) // 緑色
                            )
                        }
                    }
                }
            }

            // 3. ウィジェット配置の案内セクション
            SettingsCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.main_title_widget), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.main_widget_desc), color = bodyTextColor, fontSize = 14.sp)
                }
            }

            // アプリ終了ボタン
            Button(
                onClick = { (context as? Activity)?.finish() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSystemInDarkTheme()) Color(0xFF616161) else Color.DarkGray,
                    contentColor = Color.White
                ),
                modifier = Modifier.width(200.dp).height(50.dp)
            ) {
                Text(stringResource(R.string.main_btn_close), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // 余白の追加
            Spacer(modifier = Modifier.height(24.dp))

            // パッケージマネージャーからアプリのバージョン名を取得して表示
            val versionName = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            } catch (_: Exception) {
                "Unknown"
            }

            Text(
                text = "Version $versionName",
                color = bodyTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            // アプリ紹介ページ・ライセンスへのリンク
            LinkText(stringResource(R.string.open_info_page), INFO_URL)
            LinkText(stringResource(R.string.open_license), LICENSE_URL)

            Spacer(modifier = Modifier.height(24.dp))

            // 追加設定（折りたたみ。開閉状態は保存しないが、言語切替の再生成では維持する）
            AdditionalSettingsCard()
        }
    }
}

/** 設定画面のカード（画面背景と同じ塗り＋枠線のみ。影なし） */
@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.background),
        border = outlinedBorder(),
        content = content
    )
}

@Composable
private fun LinkText(text: String, url: String) {
    val context = LocalContext.current
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun AdditionalSettingsCard() {
    val accent = accentColor()

    var open by rememberSaveable { mutableStateOf(false) }
    var selectedTargetIndex by rememberSaveable { mutableIntStateOf(0) }

    SettingsCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        // 見出し（タップで開閉）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (open) "▼" else "▶", fontSize = 14.sp, color = accent)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.setting_extra_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
        }

        if (open) AdditionalSettingsContent(
            selectedTargetIndex = selectedTargetIndex,
            onSelectTarget = { selectedTargetIndex = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdditionalSettingsContent(selectedTargetIndex: Int, onSelectTarget: (Int) -> Unit) {
    val context = LocalContext.current
    val selectedTarget = ColorTarget.entries[selectedTargetIndex]
    var bgColors by remember {
        mutableStateOf(ColorTarget.entries.associateWith { AppSettings.getBgColor(context, it) })
    }
    var bgAlpha by remember { mutableIntStateOf(DataUsage.getBgAlpha(context)) }
    var transparencyExpanded by remember { mutableStateOf(false) }
    val language = remember { AppSettings.getLanguage(context) }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {

        // 3-1. 背景色
        SubHeading(stringResource(R.string.setting_bg_color))
        ChoiceButtons(
            labels = ColorTarget.entries.map { stringResource(it.labelRes) },
            selectedIndex = selectedTargetIndex,
            onSelect = onSelectTarget
        )
        Spacer(modifier = Modifier.height(12.dp))

        val currentColor = bgColors.getValue(selectedTarget)
        AppSettings.PALETTE.chunked(6).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowColors.forEach { palette ->
                    ColorSwatch(
                        color = palette.color,
                        name = stringResource(palette.nameRes),
                        selected = palette.color == currentColor,
                        onClick = {
                            AppSettings.setBgColor(context, selectedTarget, palette.color)
                            bgColors = bgColors + (selectedTarget to palette.color)
                            AppSettings.requestWidgetUpdate(context)
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                AppSettings.resetBgColors(context)
                bgColors = ColorTarget.entries.associateWith { it.defaultColor }
                AppSettings.requestWidgetUpdate(context)
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            border = outlinedBorder()
        ) {
            Text(stringResource(R.string.setting_reset_colors))
        }

        // 3-2. 背景の透明度
        Spacer(modifier = Modifier.height(20.dp))
        val transparencyOptions = listOf(
            "0%" to 255, "12.5%" to 223, "25%" to 191, "37.5%" to 159,
            "50%" to 128, "62.5%" to 96, "75%" to 64, "87.5%" to 32, "100%" to 0
        )
        val currentTransparencyLabel =
            transparencyOptions.firstOrNull { it.second == bgAlpha }?.first ?: "0%"

        ExposedDropdownMenuBox(
            expanded = transparencyExpanded,
            onExpandedChange = { transparencyExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentTransparencyLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.setting_bg_transparency)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transparencyExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = transparencyExpanded,
                onDismissRequest = { transparencyExpanded = false }
            ) {
                transparencyOptions.forEach { (label, alpha) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            bgAlpha = alpha
                            transparencyExpanded = false
                            context.getSharedPreferences("NetworkCheckerPrefs", Context.MODE_PRIVATE)
                                .edit { putInt("bg_alpha", alpha) }
                            AppSettings.requestWidgetUpdate(context)
                        }
                    )
                }
            }
        }

        // 3-3. プレビュー
        Spacer(modifier = Modifier.height(20.dp))
        SubHeading(stringResource(R.string.setting_preview))
        // 壁紙は表示確認用のため保存しない（言語切替の再生成では維持）
        var wallpaperIndex by rememberSaveable { mutableIntStateOf(0) }
        val wallpaper = PreviewWallpaper.entries[wallpaperIndex]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左: 壁紙に見立てた下地の中央にウィジェットのサンプル
            Box(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .wallpaper(wallpaper)
                    .padding(14.dp)
            ) {
                WidgetPreview(target = selectedTarget, bgColor = currentColor, bgAlpha = bgAlpha)
            }
            // 右: 壁紙の選択
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.setting_wallpaper),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PreviewWallpaper.entries.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { item ->
                            WallpaperSwatch(
                                wallpaper = item,
                                selected = item == wallpaper,
                                onClick = { wallpaperIndex = item.ordinal }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // 3-4. 言語
        Spacer(modifier = Modifier.height(20.dp))
        SubHeading(stringResource(R.string.setting_language))
        val languages = listOf(AppSettings.LANGUAGE_SYSTEM, AppSettings.LANGUAGE_JA, AppSettings.LANGUAGE_EN)
        ChoiceButtons(
            labels = listOf(
                stringResource(R.string.setting_language_system),
                stringResource(R.string.setting_language_ja),
                stringResource(R.string.setting_language_en)
            ),
            selectedIndex = languages.indexOf(language).coerceAtLeast(0),
            onSelect = { index ->
                val newLanguage = languages[index]
                if (newLanguage != language) {
                    AppSettings.setLanguage(context, newLanguage)
                    AppSettings.requestWidgetUpdate(context)
                    // 画面を再生成して新しい言語を反映（attachBaseContext で適用される）
                    (context as? Activity)?.recreate()
                }
            }
        )
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** 横並びの択一ボタン（選択中は塗りつぶし） */
@Composable
private fun ChoiceButtons(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val content: @Composable () -> Unit = {
                // 狭い画面でも省略されないよう、入りきらない場合は文字を縮小する
                Text(
                    label,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp)
                )
            }
            val buttonModifier = Modifier.weight(1f).semantics { selected = index == selectedIndex }
            val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            if (index == selectedIndex) {
                Button(
                    onClick = { onSelect(index) },
                    modifier = buttonModifier,
                    contentPadding = padding,
                    colors = ButtonDefaults.buttonColors(containerColor = SELECTED_BUTTON_COLOR, contentColor = Color.White)
                ) { content() }
            } else {
                OutlinedButton(
                    onClick = { onSelect(index) },
                    modifier = buttonModifier,
                    contentPadding = padding,
                    border = outlinedBorder()
                ) { content() }
            }
        }
    }
}

/** パレットの色見本（選択中は太枠とチェック） */
@Composable
private fun ColorSwatch(color: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) accentColor() else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text("✓", color = Color(AppSettings.textColor(color)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** プレビューの下地（壁紙に見立てたもの） */
private enum class PreviewWallpaper(val color: Color?, val nameRes: Int) {
    CHECKER(null, R.string.wallpaper_checker),
    WHITE(Color.White, R.string.color_white),
    GRAY(Color(0xFF9E9E9E), R.string.color_gray),
    BLACK(Color.Black, R.string.color_black),
}

/** 壁紙を描画する（市松模様は描画範囲内にクリップする） */
private fun Modifier.wallpaper(wallpaper: PreviewWallpaper): Modifier =
    clipToBounds().drawBehind {
        val solid = wallpaper.color
        if (solid != null) {
            drawRect(solid)
            return@drawBehind
        }
        val cell = 10.dp.toPx()
        drawRect(Color.White)
        var y = 0
        while (y * cell < size.height) {
            var x = 0
            while (x * cell < size.width) {
                if ((x + y) % 2 == 1) {
                    drawRect(Color(0xFFBDBDBD), Offset(x * cell, y * cell), Size(cell, cell))
                }
                x++
            }
            y++
        }
    }

/** 壁紙の色見本（選択中は太枠） */
@Composable
private fun WallpaperSwatch(wallpaper: PreviewWallpaper, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) accentColor() else MaterialTheme.colorScheme.outline
    val name = stringResource(wallpaper.nameRes)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .wallpaper(wallpaper)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
    )
}

/** ウィジェットを模した簡易プレビュー */
@Composable
private fun WidgetPreview(target: ColorTarget, bgColor: Int, bgAlpha: Int, modifier: Modifier = Modifier) {
    val textColor = Color(AppSettings.textColor(bgColor))
    val usageSample = buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 21.sp)) { append("1.234") }
        append(" GB")
    }
    Column(
        modifier = modifier
            .size(110.dp)
            .background(Color(ColorUtils.setAlphaComponent(bgColor, bgAlpha)))
            .padding(4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(target.statusRes), color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        Text(stringResource(R.string.usage_label), color = textColor, fontSize = 11.sp, maxLines = 1)
        Text(usageSample, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

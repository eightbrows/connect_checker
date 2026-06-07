package io.github.eightbrows.connect_checker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    InstructionScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 必要な権限の許可状態を保持
    var hasUsagePermission by remember { mutableStateOf(DataUsage.hasUsageAccess(context)) }

    val prefs = context.getSharedPreferences("NetworkCheckerPrefs", Context.MODE_PRIVATE)
    var startDayInput by remember { mutableIntStateOf(prefs.getInt("start_day", 1)) }
    var expanded by remember { mutableStateOf(false) }

    // データ使用量を保持する変数
    var mobileDataUsage by remember { mutableStateOf(context.getString(R.string.no_data)) }

    // 画面が開いた時や、起算日が変わった時に自動計算する
    LaunchedEffect(hasUsagePermission, startDayInput) {
        if (hasUsagePermission) {
            mobileDataUsage = DataUsage.getMobileDataUsageText(context, startDayInput)
        } else {
            mobileDataUsage = context.getString(R.string.no_permission)
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
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.main_title_permission), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 使用状況へのアクセス権限
                    if (hasUsagePermission) {
                        Text(stringResource(R.string.main_usage_granted), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.main_usage_desc), color = Color.DarkGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.main_btn_usage))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 2. 通信量計算の起算日設定セクション
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.main_title_start_day), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.main_start_day_desc), color = Color.DarkGray, fontSize = 14.sp)
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
                                                val intent = Intent(context, NetworkWidget::class.java).apply {
                                                    action = "ACTION_CHECK_NETWORK"
                                                }
                                                context.sendBroadcast(intent)
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
                                color = Color.Gray
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
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.main_title_widget), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.main_widget_desc), color = Color.DarkGray, fontSize = 14.sp)
                }
            }

            // アプリ終了ボタン
            Button(
                onClick = { (context as? Activity)?.finish() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.width(200.dp).height(50.dp)
            ) {
                Text(stringResource(R.string.main_btn_close), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // 余白の追加
            Spacer(modifier = Modifier.height(24.dp))

            // パッケージマネージャーからアプリのバージョン名を取得して表示
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "Unknown"
            }

            Text(
                text = "Version $versionName",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

// アプリ紹介ページへのリンク
            val infoUrl = "https://eightbrows.github.io/"
            Text(
                text = stringResource(R.string.open_info_page),
                color = Color.Blue,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(infoUrl)))
                    }
                    .padding(8.dp)
            )
        }
    }
}
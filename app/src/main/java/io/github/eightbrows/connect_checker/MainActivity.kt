package io.github.eightbrows.connect_checker

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
    var hasUsagePermission by remember { mutableStateOf(checkUsagePermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }

    // 通知権限リクエスト用ランチャー
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasNotificationPermission = isGranted }
    )

    val prefs = context.getSharedPreferences("NetworkCheckerPrefs", Context.MODE_PRIVATE)
    var startDayInput by remember { mutableIntStateOf(prefs.getInt("start_day", 1)) }
    var expanded by remember { mutableStateOf(false) }

    // データ使用量を保持する変数
    var mobileDataUsage by remember { mutableStateOf("---") }

    // 画面が開いた時や、起算日が変わった時に自動計算する
    LaunchedEffect(hasUsagePermission, startDayInput) {
        if (hasUsagePermission) {
            mobileDataUsage = getMobileDataUsageForActivity(context, startDayInput)
        } else {
            mobileDataUsage = context.getString(R.string.no_permission)
        }
    }

    // Android 13以上の場合、初回起動時に通知権限をリクエストする
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ライフサイクルを監視し、アプリがフォアグラウンドに戻った際に権限状態を再チェックする
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = checkUsagePermission(context)
                hasNotificationPermission = checkNotificationPermission(context)
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
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.main_btn_usage))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 通知権限（Android 13以降）
                    if (hasNotificationPermission) {
                        Text(stringResource(R.string.main_notif_granted), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.main_notif_desc), color = Color.DarkGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.main_btn_notif))
                        }
                    }
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
        }
    }
}

// 使用状況へのアクセス権限チェック
fun checkUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

// 通知権限のチェック（Android 13以降対応）
fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        // Android 12以下の場合はデフォルトで許可済みとする
        true
    }
}

// Activity用の通信量取得
fun getMobileDataUsageForActivity(context: Context, startDay: Int): String {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    if (mode != AppOpsManager.MODE_ALLOWED) {
        return context.getString(R.string.no_permission)
    }

    val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as android.app.usage.NetworkStatsManager
    val now = java.util.Calendar.getInstance()
    val endTime = now.timeInMillis

    val startCal = java.util.Calendar.getInstance()
    startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    startCal.set(java.util.Calendar.MINUTE, 0)
    startCal.set(java.util.Calendar.SECOND, 0)
    startCal.set(java.util.Calendar.MILLISECOND, 0)

    if (now.get(java.util.Calendar.DAY_OF_MONTH) < startDay) {
        startCal.add(java.util.Calendar.MONTH, -1)
    }
    startCal.set(java.util.Calendar.DAY_OF_MONTH, startDay)
    val startTime = startCal.timeInMillis

    return try {
        val bucket = networkStatsManager.querySummaryForDevice(
            android.net.ConnectivityManager.TYPE_MOBILE,
            null,
            startTime,
            endTime
        )
        val bytes = bucket.rxBytes + bucket.txBytes
        formatDataSizeForActivity(bytes)
    } catch (_: Exception) {
        "---"
    }
}

fun formatDataSizeForActivity(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) {
        return String.format(java.util.Locale.US,"%.2f GB", gb)
    }
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US,"%.0f MB", mb)
}
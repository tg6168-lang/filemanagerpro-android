package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.ui.analyzer.AnalyzerScreen
import com.example.filemanager.ui.dualpane.DualPaneScreen
import com.example.filemanager.ui.explorer.FileExplorerScreen
import com.example.filemanager.ui.gamedata.GameDataScreen
import com.example.filemanager.ui.home.HomeScreen
import com.example.filemanager.ui.settings.SettingsScreen
import com.example.filemanager.ui.source.SourcePackageScreen
import com.example.filemanager.ui.viewers.*
import com.example.filemanager.viewmodel.MainViewModel
import com.example.filemanager.viewmodel.ScreenDestination
import com.example.ui.theme.*

data class BottomNavItem(
    val title: String,
    val destination: ScreenDestination,
    val icon: ImageVector,
    val accentColor: Color
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FileManagerProTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentDir by viewModel.currentDirectory.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (viewModel.canNavigateBack()) {
            val handled = viewModel.navigateBack()
            if (!handled) {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    (context as? android.app.Activity)?.finishAffinity()
                } else {
                    lastBackPressTime = now
                    android.widget.Toast.makeText(
                        context,
                        "Vuốt lần nữa để thoát ứng dụng",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                (context as? android.app.Activity)?.finishAffinity()
            } else {
                lastBackPressTime = now
                android.widget.Toast.makeText(
                    context,
                    "Vuốt lần nữa để thoát ứng dụng",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val bottomNavItems = remember {
        listOf(
            BottomNavItem("Trang chủ", ScreenDestination.Home, Icons.Default.Home, CyanGlow),
            BottomNavItem("Tệp", ScreenDestination.Files, Icons.Default.Folder, GoldAccent),
            BottomNavItem("Công cụ", ScreenDestination.Analyzer, Icons.Default.Build, VioletGlow),
            BottomNavItem("Mã nguồn", ScreenDestination.SourcePackage, Icons.Default.Inventory2, CyanGlow)
        )
    }

    // Bottom navigation is shown on Home, Analyzer, and SourcePackage screens.
    // On Files screen, bottom bar remains hidden to maximize screen area for files.
    val showBottomBar = (currentScreen is ScreenDestination.Home ||
        currentScreen is ScreenDestination.Analyzer ||
        currentScreen is ScreenDestination.SourcePackage) &&
        currentScreen !is ScreenDestination.Files

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = ObsidianDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Column {
                        HorizontalDivider(color = CharcoalSurfaceHighlight, thickness = 0.9.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val isSelected = when (item.destination) {
                                    is ScreenDestination.Home -> currentScreen is ScreenDestination.Home
                                    is ScreenDestination.Files -> currentScreen is ScreenDestination.Files
                                    is ScreenDestination.Analyzer -> currentScreen is ScreenDestination.Analyzer
                                    is ScreenDestination.SourcePackage -> currentScreen is ScreenDestination.SourcePackage
                                    else -> false
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { viewModel.navigateTo(item.destination) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) item.accentColor.copy(alpha = 0.18f) else Color.Transparent,
                                            modifier = Modifier.size(width = 48.dp, height = 28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    item.icon,
                                                    contentDescription = item.title,
                                                    tint = if (isSelected) item.accentColor else TextMuted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.title,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) item.accentColor else TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = CharcoalBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (val screen = currentScreen) {
                is ScreenDestination.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToFiles = { dir ->
                            viewModel.loadDirectory(dir)
                            viewModel.navigateTo(ScreenDestination.Files)
                        },
                        onNavigateToGameData = { viewModel.navigateTo(ScreenDestination.GameData) },
                        onNavigateToAnalyzer = { viewModel.navigateTo(ScreenDestination.Analyzer) },
                        onNavigateToSourcePackage = { viewModel.navigateTo(ScreenDestination.SourcePackage) },
                        onNavigateToSettings = { viewModel.navigateTo(ScreenDestination.Settings) },
                        onNavigateToDualPane = { viewModel.navigateTo(ScreenDestination.DualPane) }
                    )
                }

                is ScreenDestination.Files -> {
                    FileExplorerScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.GameData -> {
                    GameDataScreen(
                        currentDirectory = currentDir,
                        onBack = { viewModel.navigateBack() },
                        onOpenHexViewer = { file -> viewModel.navigateTo(ScreenDestination.HexViewer(file)) },
                        onOpenTextViewer = { file -> viewModel.navigateTo(ScreenDestination.TextViewer(file)) },
                        onOpenDatabaseViewer = { file -> viewModel.navigateTo(ScreenDestination.SqliteViewer(file)) },
                        onOpenArchiveViewer = { file -> viewModel.navigateTo(ScreenDestination.ArchiveViewer(file)) },
                        onOpenApkInspector = { file -> viewModel.navigateTo(ScreenDestination.ApkInspector(file)) }
                    )
                }

                is ScreenDestination.Analyzer -> {
                    AnalyzerScreen(
                        currentDirectory = currentDir,
                        onBack = { viewModel.navigateBack() },
                        onOpenHexViewer = { file -> viewModel.navigateTo(ScreenDestination.HexViewer(file)) },
                        onOpenApkInspector = { file -> viewModel.navigateTo(ScreenDestination.ApkInspector(file)) },
                        onOpenDatabaseViewer = { file -> viewModel.navigateTo(ScreenDestination.SqliteViewer(file)) },
                        onOpenGameData = { viewModel.navigateTo(ScreenDestination.GameData) }
                    )
                }

                is ScreenDestination.SourcePackage -> {
                    SourcePackageScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() },
                        onOpenArchiveViewer = { file -> viewModel.navigateTo(ScreenDestination.ArchiveViewer(file)) },
                        onOpenFolderInExplorer = { dir ->
                            viewModel.openStorageRoot(dir)
                        }
                    )
                }

                is ScreenDestination.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() },
                        onNavigateToSourcePackage = { viewModel.navigateTo(ScreenDestination.SourcePackage) }
                    )
                }

                is ScreenDestination.TextViewer -> {
                    TextViewerScreen(
                        file = screen.file,
                        onBack = { viewModel.navigateBack() },
                        onOpenHexViewer = { file -> viewModel.navigateTo(ScreenDestination.HexViewer(file)) }
                    )
                }

                is ScreenDestination.ApkInspector -> {
                    ApkInspectorScreen(
                        apkFile = screen.file,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.SqliteViewer -> {
                    SqliteViewerScreen(
                        dbFile = screen.file,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.HexViewer -> {
                    HexViewerScreen(
                        file = screen.file,
                        onBack = { viewModel.navigateBack() },
                        onOpenTextViewer = { file -> viewModel.navigateTo(ScreenDestination.TextViewer(file)) }
                    )
                }

                is ScreenDestination.ArchiveViewer -> {
                    ArchiveViewerScreen(
                        archiveFile = screen.file,
                        onBack = { viewModel.navigateBack() },
                        onOpenApkInspector = { file -> viewModel.navigateTo(ScreenDestination.ApkInspector(file)) },
                        onOpenSqliteViewer = { file -> viewModel.navigateTo(ScreenDestination.SqliteViewer(file)) },
                        onOpenHexViewer = { file -> viewModel.navigateTo(ScreenDestination.HexViewer(file)) }
                    )
                }

                is ScreenDestination.MediaViewer -> {
                    MediaViewerScreen(
                        file = screen.file,
                        fileType = screen.type,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.DualPane -> {
                    DualPaneScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() },
                        onOpenFile = { fileItem -> viewModel.openFile(fileItem) }
                    )
                }
            }
        }
    }
}

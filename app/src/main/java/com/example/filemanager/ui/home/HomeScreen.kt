package com.example.filemanager.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.StorageVolumeInfo
import com.example.filemanager.ui.components.DarkPremiumBackdrop
import com.example.filemanager.ui.components.PremiumFeatureCard
import com.example.filemanager.ui.components.PremiumSectionHeader
import com.example.filemanager.ui.explorer.FileIcon
import com.example.filemanager.ui.extensions.GitHubEnvSettingsDialog
import com.example.filemanager.ui.extensions.GitHubExtensionSummaryCard
import com.example.filemanager.ui.extensions.GoogleAccountPortalCard
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.viewmodel.MainViewModel
import com.example.filemanager.viewmodel.ScreenDestination
import com.example.ui.theme.*
import java.io.File

private data class HomeCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToFiles: (File) -> Unit,
    onNavigateToGameData: () -> Unit,
    onNavigateToAnalyzer: () -> Unit,
    onNavigateToSourcePackage: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToDualPane: () -> Unit = { viewModel.navigateTo(ScreenDestination.DualPane) }
) {
    val context = LocalContext.current
    val storageVolumes by viewModel.storageVolumes.collectAsState()
    val recentFilePaths by viewModel.recentFilePaths.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val gitHubState by viewModel.gitHubPushManager.state.collectAsState()
    var showGitHubDialog by remember { mutableStateOf(false) }

    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasPermission by remember { mutableStateOf(checkPermission()) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = checkPermission()
        if (hasPermission) viewModel.refreshStorageVolumes()
    }

    val standardPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = checkPermission()
        if (hasPermission) viewModel.refreshStorageVolumes()
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            standardPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.handleSafUri(uri)
        }
    }

    val internalDir = remember { Environment.getExternalStorageDirectory() }

    val categories = remember(storageVolumes) {
        listOf(
            HomeCategory("Bộ nhớ trong", "Thư mục gốc máy", Icons.Default.PhoneAndroid, GoldAccent) {
                viewModel.openStorageRoot(internalDir)
            },
            HomeCategory("Thẻ nhớ SD", "Thẻ nhớ / SAF", Icons.Default.SdCard, CyanAccent) {
                val sd = storageVolumes.find { it.isRemovable }
                if (sd != null) {
                    viewModel.openStorageRoot(File(sd.path))
                } else {
                    safLauncher.launch(null)
                }
            }
        )
    }

    val recentFiles = remember(recentFilePaths) {
        recentFilePaths.mapNotNull { path ->
            val f = File(path)
            if (f.exists() && f.isFile) {
                FileItem(
                    name = f.name,
                    path = f.absolutePath,
                    size = f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = false,
                    isFavorite = false,
                    fileType = FileUtils.detectFileType(f),
                    mimeType = FileUtils.getMimeType(f.name),
                    extension = FileUtils.getExtension(f.name),
                    childCount = 0,
                    isReadable = f.canRead(),
                    isWritable = f.canWrite()
                )
            } else null
        }.take(6)
    }

    Scaffold(
        topBar = {
            Surface(
                color = ObsidianDeep.copy(alpha = 0.94f),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF070E1E),
                                border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.55f)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_dark_premium_1795151421027),
                                    contentDescription = "Biểu tượng File Manager Pro",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                                            append("File Manager ")
                                        }
                                        withStyle(SpanStyle(color = GoldAccent, fontWeight = FontWeight.ExtraBold)) {
                                            append("Pro")
                                        }
                                    },
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Quản lý tệp & Công cụ dữ liệu Android",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HeaderSquareIconButton(
                                icon = Icons.Default.Search,
                                contentDescription = "Tìm kiếm",
                                tint = CyanAccent,
                                onClick = {
                                    viewModel.openStorageRoot(internalDir)
                                    viewModel.setSearchQuery("")
                                }
                            )
                            HeaderSquareIconButton(
                                icon = Icons.Default.Settings,
                                contentDescription = "Cài đặt",
                                tint = GoldAccent,
                                onClick = onNavigateToSettings
                            )
                        }
                    }
                    HorizontalDivider(color = CharcoalSurfaceHighlight, thickness = 0.8.dp)
                }
            }
        },
        containerColor = CharcoalBackground
    ) { padding ->
        DarkPremiumBackdrop(
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Storage Permission Banner (if needed)
                if (!hasPermission) {
                    item {
                        Surface(
                            color = GoldAccent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.48f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoldAccent.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            tint = GoldAccent,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cần quyền truy cập bộ nhớ",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Cấp quyền quản lý toàn bộ tệp trên thiết bị.",
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { requestStoragePermission() },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Cấp quyền", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Primary Large Feature Cards (matching Dark Premium reference layout)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PremiumFeatureCard(
                            title = "Duyệt tệp & Bộ nhớ",
                            subtitle = "Quản lý thư mục, nén ZIP/7z/TAR & xem ảnh thu nhỏ",
                            icon = Icons.Default.FolderOpen,
                            accentColor = CyanAccent,
                            badgeText = "CORE",
                            modifier = Modifier.testTag("home_browse_files_card")
                        ) {
                            viewModel.openStorageRoot(internalDir)
                        }

                        PremiumFeatureCard(
                            title = "Cửa sổ kép (Dual Pane)",
                            subtitle = "Chia đôi màn hình, kéo thả & sao chép giữa 2 thư mục",
                            icon = Icons.Default.Splitscreen,
                            accentColor = ElectricBlue,
                            modifier = Modifier.testTag("home_dual_pane_card")
                        ) {
                            viewModel.toggleDualPane(true)
                            viewModel.openStorageRoot(internalDir)
                        }
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "TÍNH NĂNG NỔI BẬT",
                        accentColor = CyanAccent
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PremiumFeatureCard(
                            title = "Dữ liệu Game (OBB / PAK / Asset)",
                            subtitle = "Nhận diện UnityFS, Unreal PAK, DEX & Entropy mã hóa",
                            icon = Icons.Default.SportsEsports,
                            accentColor = VioletGlow,
                            modifier = Modifier.testTag("home_game_data_card")
                        ) {
                            onNavigateToGameData()
                        }

                        PremiumFeatureCard(
                            title = "Công cụ phân tích & Kiểm tra",
                            subtitle = "APK Inspector, SQLite Viewer, Hex Editor, Hash & Trùng lặp",
                            icon = Icons.Default.BuildCircle,
                            accentColor = GoldAccent,
                            modifier = Modifier.testTag("home_analyzer_card")
                        ) {
                            onNavigateToAnalyzer()
                        }

                        PremiumFeatureCard(
                            title = "Tải về mã nguồn cập nhật (.ZIP)",
                            subtitle = "Đóng gói đầy đủ tệp cập nhật & đẩy 1-chạm lên GitHub",
                            icon = Icons.Default.Inventory2,
                            accentColor = CyanGlow,
                            badgeText = "MỚI",
                            modifier = Modifier.testTag("home_source_package_card")
                        ) {
                            onNavigateToSourcePackage()
                        }
                    }
                }

                // Storage Volume Section
                item {
                    PremiumSectionHeader(
                        title = "BỘ NHỚ THIẾT BỊ",
                        accentColor = GoldAccent
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val primaryVolume = storageVolumes.find { it.isPrimary } ?: StorageVolumeInfo(
                        name = "Bộ nhớ trong",
                        path = internalDir.absolutePath,
                        totalBytes = 64L * 1024 * 1024 * 1024,
                        freeBytes = 32L * 1024 * 1024 * 1024,
                        isPrimary = true
                    )

                    CompactStorageCard(
                        volume = primaryVolume,
                        onClick = { viewModel.openStorageRoot(File(primaryVolume.path)) }
                    )

                    val removableVolumes = storageVolumes.filter { !it.isPrimary }
                    if (removableVolumes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        removableVolumes.forEach { vol ->
                            CompactStorageCard(
                                volume = vol,
                                onClick = { viewModel.openStorageRoot(File(vol.path)) }
                            )
                        }
                    }
                }

                // Storage Category Pair Cards
                item {
                    PremiumSectionHeader(
                        title = "TRUY CẬP NHANH BỘ NHỚ",
                        accentColor = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val chunkedCategories = categories.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunkedCategories.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CategoryRowCard(
                                    category = pair[0],
                                    modifier = Modifier.weight(1f)
                                )
                                if (pair.size > 1) {
                                    CategoryRowCard(
                                        category = pair[1],
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Extensions Section (Google Auth Portal & GitHub APK CI/CD)
                item {
                    PremiumSectionHeader(
                        title = "TIỆN ÍCH MỞ RỘNG (GOOGLE & GITHUB APK)",
                        accentColor = VioletGlow
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoogleAccountPortalCard()
                        GitHubExtensionSummaryCard(
                            gitHubState = gitHubState,
                            onOpenSettingsAndPushDialog = { showGitHubDialog = true }
                        )
                    }
                }

                // Recent Files Section
                if (recentFiles.isNotEmpty()) {
                    item {
                        PremiumSectionHeader(
                            title = "TỆP GẦN ĐÂY",
                            accentColor = CyanAccent
                        )
                    }

                    items(recentFiles, key = { it.path }) { item ->
                        RecentFileRow(item = item, onClick = { viewModel.openFile(item) })
                    }
                }
            }
        }
    }

    if (showGitHubDialog) {
        GitHubEnvSettingsDialog(
            pushManager = viewModel.gitHubPushManager,
            defaultDirectory = currentDirectory,
            onDismiss = { showGitHubDialog = false }
        )
    }
}

@Composable
private fun HeaderSquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CharcoalSurfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, CharcoalSurfaceHighlight),
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun CompactStorageCard(
    volume: StorageVolumeInfo,
    onClick: () -> Unit
) {
    val usedBytes = volume.totalBytes - volume.freeBytes
    val percentUsed = if (volume.totalBytes > 0) {
        (usedBytes.toFloat() / volume.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val accent = if (volume.isPrimary) GoldAccent else CyanAccent
    val barColor = if (percentUsed > 0.9f) ColorErrorRed else accent

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.1f), ObsidianCardTop, ObsidianCardBottom)
                )
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (volume.isPrimary) Icons.Default.PhoneAndroid else Icons.Default.SdCard,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = volume.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${(percentUsed * 100).toInt()}% đã dùng",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (percentUsed > 0.9f) ColorErrorRed else accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(7.dp))

                LinearProgressIndicator(
                    progress = { percentUsed },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = barColor,
                    trackColor = CharcoalSurfaceHighlight
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "${FileItem.formatBytes(usedBytes)} / ${FileItem.formatBytes(volume.totalBytes)} • Trống ${FileItem.formatBytes(volume.freeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CategoryRowCard(
    category: HomeCategory,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(1.dp, category.iconColor.copy(alpha = 0.35f)),
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(category.iconColor.copy(alpha = 0.1f), ObsidianCardTop, ObsidianCardBottom)
                )
            )
            .clickable { category.onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = category.iconColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, category.iconColor.copy(alpha = 0.45f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        tint = category.iconColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
                Text(
                    text = category.subtitle,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

@Composable
private fun RecentFileRow(
    item: FileItem,
    onClick: () -> Unit
) {
    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CharcoalSurfaceHighlight),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(item = item, isSelected = false, modifier = Modifier.size(38.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${FileItem.formatBytes(item.size)} • ${FileItem.formatDate(item.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
        }
    }
}

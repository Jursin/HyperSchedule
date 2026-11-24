package top.jursin.hyperschedule.ui.page.settings

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import top.jursin.hyperschedule.R
import top.jursin.hyperschedule.getAppVersion
import top.jursin.hyperschedule.getAppVersionCode
import top.jursin.hyperschedule.theme.CardCornerRadius
import top.jursin.hyperschedule.theme.PageBottomInset
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.theme.isDarkTheme
import top.jursin.hyperschedule.ui.component.BlurredTopAppBar
import top.jursin.hyperschedule.ui.component.LocalPageHorizontalMargin
import top.jursin.hyperschedule.ui.component.LocalPageTopPadding
import top.jursin.hyperschedule.ui.component.bouncyScroll
import top.jursin.hyperschedule.ui.component.pref.ArrowPreference
import top.jursin.hyperschedule.ui.effect.BgEffectBackground
import top.jursin.hyperschedule.ui.effect.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AboutPage(onBack: () -> Unit, onLicenseClick: () -> Unit, onSponsorClick: () -> Unit, appBlur: Boolean = false) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val appIcon: ImageBitmap? =
        remember {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
                val bitmap = createBitmap(size, size)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(android.graphics.Canvas(bitmap))
                bitmap.asImageBitmap()
            }.getOrNull()
        }
    val versionInfoText = "v${getAppVersion(context)} (${getAppVersionCode(context)})"

    val topBarBackdrop = rememberAppBlurBackdrop(appBlur)

    // 滚动状态与头部渐隐进度
    val lazyListState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val scrollProgress by
        remember {
            derivedStateOf {
                if (lazyListState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    val spacerSize =
                        lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == "logoSpacer" }
                            ?.size ?: 0
                    if (spacerSize > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacerSize).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }

    Scaffold(
        topBar = {
            BlurredTopAppBar(
                title = "关于",
                blurBackdrop = topBarBackdrop,
                scrollBehavior = topAppBarScrollBehavior,
                onBack = onBack,
                pinnedTitle = true,
                color =
                    if (topBarBackdrop == null && scrollProgress == 1f) {
                        MiuixTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    },
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
            )
        },
    ) { innerPadding ->
        Box(modifier = topBarBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier) {
            AboutContentBody(
                padding = innerPadding,
                appIcon = appIcon,
                versionInfoText = versionInfoText,
                lazyListState = lazyListState,
                scrollProgressProvider = { scrollProgress },
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                onLicenseClicked = onLicenseClick,
                onSponsorClick = onSponsorClick,
                uriHandler = uriHandler,
                useBlur = appBlur,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AboutContentBody(
    padding: PaddingValues,
    appIcon: ImageBitmap?,
    versionInfoText: String,
    lazyListState: LazyListState,
    scrollProgressProvider: () -> Float,
    topAppBarScrollBehavior: ScrollBehavior,
    onLicenseClicked: () -> Unit,
    onSponsorClick: () -> Unit,
    uriHandler: UriHandler,
    useBlur: Boolean,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val isDark = isDarkTheme()

    // 图标/名称渐变取色层，不跟随模糊开关，只要支持 RuntimeShader 就始终启用
    val gradientBackdrop = rememberAppBlurBackdrop(true)
    val cardBackdrop = if (useBlur) gradientBackdrop else null
    val logoBlend =
        remember(isDark) {
            if (isDark) {
                listOf(
                    BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                    BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                    BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
                )
            } else {
                listOf(
                    BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                    BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                    BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
                )
            }
        }

    // 卡片半透明天色，透出底部渐变
    val cardBlend =
        remember(isDark) {
            if (isDark) {
                listOf(BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity))
            } else {
                listOf(
                    BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                    BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
                )
            }
        }

    val logoColors = BlurDefaults.blurColors(blendColors = logoBlend)

    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val displayCutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val pageMargin = LocalPageHorizontalMargin.current
    val pageTopPadding = LocalPageTopPadding.current

    val listContentPadding =
        PaddingValues(
            start =
                horizontalSafeInsets.calculateStartPadding(layoutDirection) +
                    displayCutoutInsets.calculateLeftPadding(layoutDirection) + pageMargin,
            top = padding.calculateTopPadding() + pageTopPadding,
            end =
                horizontalSafeInsets.calculateEndPadding(layoutDirection) +
                    displayCutoutInsets.calculateRightPadding(layoutDirection) + pageMargin,
            bottom = padding.calculateBottomPadding() + PageBottomInset,
        )

    val logoPadding =
        PaddingValues(
            top = padding.calculateTopPadding() + pageTopPadding + 40.dp,
            start =
                horizontalSafeInsets.calculateStartPadding(layoutDirection) +
                    displayCutoutInsets.calculateLeftPadding(layoutDirection),
            end =
                horizontalSafeInsets.calculateEndPadding(layoutDirection) +
                    displayCutoutInsets.calculateRightPadding(layoutDirection),
        )

    BgEffectBackground(
        isDark = isDark,
        modifier = Modifier.fillMaxSize(),
        bgModifier = gradientBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
        alpha = { 1f - scrollProgressProvider() },
    ) {
        // 吸顶渐隐头部
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = logoPadding.calculateTopPadding() + 52.dp,
                        start = logoPadding.calculateStartPadding(layoutDirection),
                        end = logoPadding.calculateEndPadding(layoutDirection),
                    )
                    .onSizeChanged { size ->
                        with(density) { logoHeightDp = size.height.toDp() }
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            val iconProgress = ((scrollProgressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1 - iconProgress
                            scaleX = 1 - (iconProgress * 0.05f)
                            scaleY = 1 - (iconProgress * 0.05f)
                        },
            ) {
                if (gradientBackdrop != null) {
                    // 仅用前景剪影，经 logoBlend 渐变着色后以 DstIn 裁出图标轮廓
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .requiredSize(160.dp)
                                .textureBlur(
                                    backdrop = gradientBackdrop,
                                    shape = RoundedCornerShape(20.dp),
                                    blurRadius = BlurDefaults.MaxBlurRadius,
                                    colors = logoColors,
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                ),
                    )
                } else if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                    )
                }
            }

            Text(
                text = "HyperSchedule",
                modifier =
                    Modifier
                        .padding(top = 12.dp, bottom = 5.dp)
                        .graphicsLayer {
                            val nameProgress = ((scrollProgressProvider() - 0.2f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1 - nameProgress
                            scaleX = 1 - (nameProgress * 0.05f)
                            scaleY = 1 - (nameProgress * 0.05f)
                        }
                        .then(
                            if (gradientBackdrop != null) {
                                Modifier.textureBlur(
                                    backdrop = gradientBackdrop,
                                    shape = RoundedCornerShape(16.dp),
                                    blurRadius = BlurDefaults.MaxBlurRadius,
                                    colors = logoColors,
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                )
                            } else {
                                Modifier
                            }
                        ),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 35.sp,
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val versionProgress = ((scrollProgressProvider() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1 - versionProgress
                            scaleX = 1 - (versionProgress * 0.05f)
                            scaleY = 1 - (versionProgress * 0.05f)
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    text = versionInfoText,
                    style = MiuixTheme.textStyles.body2,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 可滚动内容区
        LazyColumn(
            state = lazyListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .bouncyScroll(topAppBarScrollBehavior)
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = listContentPadding,
        ) {
            // 与头部等高的透明占位
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - listContentPadding.calculateTopPadding() + 126.dp,
                        ),
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "about_content") {
                Box {
                    Spacer(Modifier.fillParentMaxHeight())
                    Column(
                        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                    ) {
                        AboutInfoCard(cardBackdrop, cardBlend) {
                            ArrowPreference(
                                title = "查看源代码",
                                endActions = {
                                    Text(
                                        text = "GitHub",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                onClick = { uriHandler.openUri("https://github.com/Jursin/HyperSchedule") },
                            )
                            ArrowPreference(
                                title = "许可证",
                                endActions = {
                                    Text(
                                        text = "Apache-2.0",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                onClick = {
                                    uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                },
                            )
                            ArrowPreference(
                                title = "开放源代码许可",
                                summary = "查看应用所使用的第三方开源库及其许可证信息",
                                onClick = onLicenseClicked,
                            )
                        }
                        AboutInfoCard(cardBackdrop, cardBlend) {
                            ArrowPreference(
                                title = "问题反馈",
                                endActions = {
                                    Text(
                                        text = "GitHub Issues",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                onClick = { uriHandler.openUri("https://github.com/Jursin/HyperSchedule/issues") },
                            )
                            ArrowPreference("赞助支持", onClick = onSponsorClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutInfoCard(
    backdrop: LayerBackdrop?,
    blendColors: List<BlendColorEntry>,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BlurDefaults.blurColors(blendColors = blendColors)
    Card(
        modifier =
            Modifier.then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(CardCornerRadius),
                        blurRadius = 60f,
                        colors = colors,
                    )
                } else {
                    Modifier
                }
            ),
        cornerRadius = CardCornerRadius,
        colors =
            CardDefaults.defaultColors(
                if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                Color.Transparent,
            ),
    ) {
        content()
    }
}

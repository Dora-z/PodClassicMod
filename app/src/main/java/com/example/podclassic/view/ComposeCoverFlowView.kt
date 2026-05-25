package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.MusicList
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sign

private const val INITIAL_INDEX = 0
private const val MAX_CACHE_SIZE = 72
private const val INITIAL_PRELOAD_RADIUS = 7
private const val FAST_PRELOAD_RADIUS = 8
private const val NORMAL_PRELOAD_RADIUS = 6

private val reflectedBitmapCache = LinkedHashMap<String, Bitmap?>()
private val preloadingKeys = mutableSetOf<String>()
private val cacheLock = Any()
private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class ComposeCoverFlowView(context: Context) : FrameLayout(context), ScreenView {

    private val albums = MediaStoreUtil.getAlbumList()
    private val initialIndex = if (albums.isEmpty()) 0 else INITIAL_INDEX.coerceAtMost(albums.size - 1)

    private val currentIndexState = mutableIntStateOf(initialIndex)
    private val targetIndexState = mutableIntStateOf(initialIndex)
    private val animatedIndexState = mutableFloatStateOf(initialIndex.toFloat())
    private val lastSlideTimeState = mutableLongStateOf(0L)
    private val bitmapCacheVersionState = mutableIntStateOf(0)

    private val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
        setContent {
            CoverFlowContent(
                albums = albums,
                currentIndexState = currentIndexState,
                targetIndexState = targetIndexState,
                animatedIndexState = animatedIndexState,
                lastSlideTimeState = lastSlideTimeState,
                bitmapCacheVersionState = bitmapCacheVersionState,
                requestPreload = ::requestPreload
            )
        }
    }

    init {
        setBackgroundColor(Colors.white)
        addTouchHandler()
        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    private fun addTouchHandler() {
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val swipeThreshold = 60f
                if (abs(diffX) <= swipeThreshold) return false

                slide(if (diffX > 0) -1 else 1)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val centerX = width / 2f
                val centerY = height / 2f
                val tapThreshold = 150f * resources.displayMetrics.density

                return abs(e.x - centerX) < tapThreshold &&
                    abs(e.y - centerY) < tapThreshold &&
                    enter()
            }
        })

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        isClickable = true
        isFocusable = true
    }

    override fun enter(): Boolean {
        if (currentIndexState.intValue !in albums.indices) return false

        Core.addView(MusicListView(context, albums[currentIndexState.intValue]))
        return true
    }

    override fun enterLongClick(): Boolean = false

    override fun getTitle(): String = Strings.COVER_FLOW

    override fun onViewAdd() {
        requestPreload(INITIAL_PRELOAD_RADIUS)
    }

    override fun onViewDelete() {
    }

    override fun slide(slideVal: Int): Boolean {
        val newIndex = targetIndexState.intValue + slideVal
        if (newIndex !in albums.indices) return false

        lastSlideTimeState.longValue = System.currentTimeMillis()
        targetIndexState.intValue = newIndex
        requestPreload(FAST_PRELOAD_RADIUS)
        return true
    }

    private fun requestPreload(preloadRadius: Int) {
        val centerIndex = targetIndexState.intValue
        preloadScope.launch {
            val cacheUpdated = preloadVisibleImages(albums, centerIndex, preloadRadius)
            if (cacheUpdated) {
                post {
                    bitmapCacheVersionState.intValue += 1
                }
            }
        }
    }

    companion object {
        suspend fun preloadVisibleImages(
            albums: List<MusicList>,
            centerIndex: Int,
            preloadRadius: Int = 3
        ): Boolean {
            var cacheUpdated = false

            for (offset in -preloadRadius..preloadRadius) {
                val albumIndex = centerIndex + offset
                if (albumIndex !in albums.indices) continue

                val albumId = albums[albumIndex].id ?: continue
                val cacheKey = "${albumId}_reflected"

                synchronized(cacheLock) {
                    if (reflectedBitmapCache.containsKey(cacheKey) || cacheKey in preloadingKeys) {
                        continue
                    }
                    preloadingKeys += cacheKey
                }

                val reflectedBitmap = try {
                    val original = MediaUtil.getAlbumImage(albumId)
                    if (original != null) {
                        val normalized = createSquareBitmapWithCenterCrop(original, 600)
                        val result = createReflectedBitmap(normalized)
                        if (normalized !== original) {
                            normalized.recycle()
                        }
                        result
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }

                synchronized(cacheLock) {
                    preloadingKeys -= cacheKey
                    trimCacheIfNeeded()
                    reflectedBitmapCache[cacheKey] = reflectedBitmap
                }
                cacheUpdated = true
            }

            return cacheUpdated
        }

        fun getCachedReflectedBitmap(albumId: Long): Bitmap? {
            val cacheKey = "${albumId}_reflected"
            synchronized(cacheLock) {
                return reflectedBitmapCache[cacheKey]
            }
        }

        private fun trimCacheIfNeeded() {
            while (reflectedBitmapCache.size >= MAX_CACHE_SIZE) {
                val eldestKey = reflectedBitmapCache.entries.firstOrNull()?.key ?: break
                reflectedBitmapCache.remove(eldestKey)?.recycle()
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun CoverFlowContent(
    albums: List<MusicList>,
    currentIndexState: androidx.compose.runtime.MutableIntState,
    targetIndexState: androidx.compose.runtime.MutableIntState,
    animatedIndexState: androidx.compose.runtime.MutableFloatState,
    lastSlideTimeState: androidx.compose.runtime.MutableLongState,
    bitmapCacheVersionState: androidx.compose.runtime.MutableIntState,
    requestPreload: (Int) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetIndex by remember { derivedStateOf { targetIndexState.intValue } }
    val bitmapCacheVersion = bitmapCacheVersionState.intValue

    val timeSinceLastSlide = System.currentTimeMillis() - lastSlideTimeState.longValue
    val isFastScroll = timeSinceLastSlide in 1..180
    val indexDistance = abs(targetIndex - currentIndexState.intValue).coerceAtLeast(1)
    val durationMs = ((if (isFastScroll) 170 else 240) + (indexDistance - 1) * 35)
        .coerceAtMost(if (isFastScroll) 230 else 320)

    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        label = "coverflow_scroll"
    )

    LaunchedEffect(animatedIndex) {
        animatedIndexState.floatValue = animatedIndex
    }

    LaunchedEffect(targetIndex) {
        currentIndexState.intValue = targetIndex.coerceIn(0, (albums.size - 1).coerceAtLeast(0))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        val metrics = remember(containerWidth, containerHeight, density, configuration.orientation) {
            val coverSize = 124.dp
            val coverSizePx = with(density) { coverSize.toPx() }
            val screenWidthPx = with(density) { containerWidth.toPx() }
            val screenHeightPx = with(density) { containerHeight.toPx() }
            val itemSpacing = coverSizePx * 0.72f

            CoverFlowMetrics(
                coverSizeDp = coverSize,
                coverSizePx = coverSizePx,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                itemSpacing = itemSpacing,
                layoutParams = CoverFlowLayoutParams(
                    coverSizePx = coverSizePx,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    itemSpacing = itemSpacing,
                    maxRotation = 60f,
                    minScale = 0.72f,
                    maxScale = 1f,
                    minAlpha = 0.54f,
                    maxAlpha = 1f,
                    cameraDistance = 8800f,
                    maxSideCount = 3,
                    unifiedSpacingMultiplier = 1f
                )
            )
        }

        val coverFlowData = remember(animatedIndex, metrics, targetIndex, albums.size, bitmapCacheVersion) {
            List(metrics.layoutParams.displayCount) { displayPos ->
                calculateCoverFlowItem(
                    displayPos = displayPos,
                    centerOffset = metrics.layoutParams.centerOffset,
                    animatedIndex = animatedIndex,
                    targetIndex = targetIndex,
                    params = metrics.layoutParams,
                    albumsSize = albums.size
                )
            }
        }

        val preloadedState = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val cacheUpdated = withContext(Dispatchers.IO) {
                ComposeCoverFlowView.preloadVisibleImages(albums, targetIndex, INITIAL_PRELOAD_RADIUS)
            }
            if (cacheUpdated) {
                bitmapCacheVersionState.intValue += 1
            }
            preloadedState.value = true
        }

        LaunchedEffect(targetIndex, isFastScroll) {
            requestPreload(if (isFastScroll) FAST_PRELOAD_RADIUS else NORMAL_PRELOAD_RADIUS)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(Colors.white))
        ) {
            if (preloadedState.value) {
                AndroidView(
                    factory = { ctx ->
                        CoverFlowNativeView(ctx).apply {
                            setContainerSize(
                                with(density) { containerWidth.toPx().toInt() },
                                with(density) { containerHeight.toPx().toInt() }
                            )
                        }
                    },
                    update = { nativeView ->
                        coverFlowData.forEach { data ->
                            val albumId = if (data.isPlaceholder) null else albums[data.albumIndex].id
                            val bitmap = if (data.isPlaceholder || albumId == null) {
                                null
                            } else {
                                ComposeCoverFlowView.getCachedReflectedBitmap(albumId)
                            }

                            nativeView.updateImage(
                                position = data.displayPos,
                                albumId = albumId,
                                transform = CoverFlowNativeView.TransformData(
                                    bitmap = bitmap,
                                    x = data.transform.x,
                                    y = data.transform.y,
                                    width = metrics.coverSizePx,
                                    height = metrics.coverSizePx * 1.5f,
                                    zIndex = data.transform.zIndex,
                                    rotationY = data.transform.rotationY,
                                    translationZ = data.transform.translationZ ?: 0f,
                                    scaleX = data.transform.scaleX * data.transform.scale,
                                    scaleY = data.transform.scaleY * data.transform.scale,
                                    translationX = data.transform.translationX,
                                    translationY = data.transform.translationY,
                                    alpha = data.transform.alpha,
                                    rotationX = data.transform.rotationX,
                                    cameraDistance = data.transform.cameraDistance,
                                    transformOriginX = data.transform.transformOriginX,
                                    transformOriginY = data.transform.transformOriginY,
                                    modeScaleCompensation = 1f
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            val titleOffsetY = with(density) {
                (metrics.layoutParams.centerY + metrics.coverSizePx * 0.86f).toDp()
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .offset(y = titleOffsetY)
                    .padding(horizontal = 16.dp)
                    .zIndex(200f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val visualCenterIndex = animatedIndex.roundToInt().coerceIn(0, (albums.size - 1).coerceAtLeast(0))
                val currentAlbum = if (visualCenterIndex in albums.indices) albums[visualCenterIndex] else null
                currentAlbum?.let { album ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = album.title,
                            color = Color.Black,
                            fontSize = 16.sp,
                            lineHeight = 18.sp,
                            minLines = 1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = album.subtitle,
                            color = Color.DarkGray,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private data class CoverFlowData(
    val displayPos: Int,
    val albumIndex: Int,
    val isPlaceholder: Boolean,
    val transform: CoverTransform
)

private data class CoverFlowMetrics(
    val coverSizeDp: Dp,
    val coverSizePx: Float,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val itemSpacing: Float,
    val layoutParams: CoverFlowLayoutParams
)

private data class CoverTransform(
    val x: Float,
    val y: Float,
    val rotationY: Float,
    val rotationX: Float,
    val scale: Float,
    val scaleX: Float,
    val scaleY: Float,
    val zIndex: Float,
    val alpha: Float,
    val transformOriginX: Float,
    val transformOriginY: Float,
    val translationX: Float,
    val translationY: Float,
    val cameraDistance: Float,
    val translationZ: Float?
)

private data class CoverFlowLayoutParams(
    val coverSizePx: Float,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val itemSpacing: Float,
    val maxRotation: Float,
    val minScale: Float,
    val maxScale: Float,
    val minAlpha: Float,
    val maxAlpha: Float,
    val cameraDistance: Float,
    val maxSideCount: Int,
    val unifiedSpacingMultiplier: Float
) {
    val displayCount: Int get() = maxSideCount * 2 + 1
    val centerOffset: Int get() = displayCount / 2
    val centerX: Float get() = screenWidthPx / 2f
    val centerY: Float get() = screenHeightPx * 0.41f
}

private fun calculateCoverFlowItem(
    displayPos: Int,
    centerOffset: Int,
    animatedIndex: Float,
    targetIndex: Int,
    params: CoverFlowLayoutParams,
    albumsSize: Int
): CoverFlowData {
    val anchorIndex = animatedIndex.roundToInt()
    val rawAlbumIndex = anchorIndex + displayPos - centerOffset
    val isPlaceholder = rawAlbumIndex !in 0 until albumsSize
    val albumIndex = rawAlbumIndex.coerceIn(0, (albumsSize - 1).coerceAtLeast(0))

    val relativeOffset = albumIndex - animatedIndex
    val absOffset = abs(relativeOffset)
    val clampedOffset = relativeOffset.coerceIn(
        -params.maxSideCount.toFloat() - 0.5f,
        params.maxSideCount.toFloat() + 0.5f
    )
    val distanceT = (abs(clampedOffset) / params.maxSideCount.toFloat()).coerceIn(0f, 1f)
    val easedDistanceT = easeInOutCubic(distanceT)
    val spreadT = easeOutCubic(((absOffset - 0.08f) / 0.92f).coerceIn(0f, 1f))
    val neighborBoost = (1f - abs(absOffset - 1f).coerceAtMost(1f))
    val centerBoost = (1f - absOffset.coerceIn(0f, 1f))
    val outerBoost = ((absOffset - 1f) / (params.maxSideCount - 1).toFloat()).coerceIn(0f, 1f)
    val signedOffset = sign(relativeOffset)

    val centeredX = params.centerX - params.coverSizePx / 2f
    val edgeOverlap = params.coverSizePx * 0.075f
    val anchoredNeighborX = when {
        signedOffset < 0f -> centeredX - params.coverSizePx + edgeOverlap
        signedOffset > 0f -> centeredX + params.coverSizePx - edgeOverlap
        else -> centeredX
    }
    val outerSpacing = params.coverSizePx * 0.34f
    val finalX = when {
        absOffset <= 1f -> lerp(centeredX, anchoredNeighborX, easeInOutCubic(absOffset))
        signedOffset == 0f -> centeredX
        else -> anchoredNeighborX + signedOffset *
            (absOffset - 1f) * outerSpacing * (0.96f + outerBoost * 0.08f)
    }
    val finalY = params.centerY - params.coverSizePx / 2f

    val rotationY = -signedOffset * spreadT * (params.maxRotation * (0.92f + neighborBoost * 0.08f))
    val rotationX = 0.75f + centerBoost * 0.16f
    val scaleAmount = when {
        absOffset <= 1f -> lerp(params.maxScale, 0.91f, easeInOutCubic(absOffset))
        else -> lerp(0.91f, params.minScale, ((absOffset - 1f) / (params.maxSideCount - 1).toFloat()).coerceIn(0f, 1f))
    }
    val scaleY = scaleAmount + centerBoost * 0.02f
    val alpha = if (isPlaceholder) 0.24f else lerp(params.maxAlpha, params.minAlpha, easedDistanceT * 0.92f)

    val zIndex = when {
        absOffset < 0.12f -> 240f
        absOffset <= 1f -> 190f - absOffset * 18f
        else -> 120f - (absOffset - 1f) * 14f
    }
    val translationZ = when {
        absOffset < 0.12f -> 36f
        absOffset <= 1f -> 18f - absOffset * 6f
        else -> 6f - (absOffset - 1f) * 4f
    }

    val transformOriginX = when {
        absOffset < 0.12f -> 0.5f
        relativeOffset < 0f -> 1f
        else -> 0f
    }

    return CoverFlowData(
        displayPos = displayPos,
        albumIndex = albumIndex,
        isPlaceholder = isPlaceholder,
        transform = CoverTransform(
            x = finalX,
            y = finalY,
            rotationY = rotationY,
            rotationX = rotationX,
            scale = 1f,
            scaleX = scaleAmount,
            scaleY = scaleY,
            zIndex = zIndex,
            alpha = alpha,
            transformOriginX = transformOriginX,
            transformOriginY = 0.5f,
            translationX = 0f,
            translationY = 0f,
            cameraDistance = params.cameraDistance,
            translationZ = translationZ
        )
    )
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private fun easeInOutCubic(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return if (t < 0.5f) {
        4f * t * t * t
    } else {
        1f - (-2f * t + 2f).pow(3) / 2f
    }
}

private fun easeOutCubic(value: Float): Float {
    val t = 1f - value.coerceIn(0f, 1f)
    return 1f - t * t * t
}

private fun createReflectedBitmap(bitmap: Bitmap): Bitmap {
    val reflectionGap = 0
    val width = bitmap.width
    val height = bitmap.height

    val matrix = Matrix().apply { preScale(1f, -1f) }
    val reflectionImage = Bitmap.createBitmap(bitmap, 0, height / 2, width, height / 2, matrix, false)
    val result = Bitmap.createBitmap(width, height + height / 2 + reflectionGap, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    canvas.drawBitmap(bitmap, 0f, 0f, null)
    canvas.drawBitmap(reflectionImage, 0f, (height + reflectionGap).toFloat(), null)
    reflectionImage.recycle()

    val paint = Paint()
    paint.shader = LinearGradient(
        0f,
        (height + reflectionGap).toFloat(),
        0f,
        result.height.toFloat(),
        0x70ffffff,
        0x00ffffff,
        Shader.TileMode.CLAMP
    )
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    canvas.drawRect(0f, height.toFloat(), width.toFloat(), result.height.toFloat(), paint)

    return result
}

private fun createSquareBitmapWithCenterCrop(source: Bitmap, targetSize: Int): Bitmap {
    val srcWidth = source.width
    val srcHeight = source.height
    if (srcWidth == targetSize && srcHeight == targetSize) {
        return source
    }

    val scale = targetSize / minOf(srcWidth, srcHeight).toFloat()
    val scaledWidth = (srcWidth * scale).toInt()
    val scaledHeight = (srcHeight * scale).toInt()
    val scaledBitmap = if (scale != 1f) {
        Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
    } else {
        source
    }

    val left = ((scaledWidth - targetSize) / 2f).coerceAtLeast(0f).toInt()
    val top = ((scaledHeight - targetSize) / 2f).coerceAtLeast(0f).toInt()

    return Bitmap.createBitmap(
        scaledBitmap,
        left,
        top,
        targetSize.coerceAtMost(scaledBitmap.width - left),
        targetSize.coerceAtMost(scaledBitmap.height - top)
    )
}

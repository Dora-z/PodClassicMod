package com.example.podclassic.view

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

class CoverFlowNativeView(context: Context) : FrameLayout(context) {
    data class TransformData(
        val bitmap: Bitmap?,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val zIndex: Float,
        val rotationY: Float,
        val translationZ: Float,
        val scaleX: Float,
        val scaleY: Float,
        val translationX: Float,
        val translationY: Float,
        val alpha: Float,
        val rotationX: Float,
        val cameraDistance: Float,
        val transformOriginX: Float,
        val transformOriginY: Float,
        val modeScaleCompensation: Float
    )

    private val imageViews = List(7) {
        AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = GONE
        }
    }
    private val albumIds = LongArray(7) { Long.MIN_VALUE }

    private var containerWidthPx = 0
    private var containerHeightPx = 0

    init {
        clipChildren = false
        clipToPadding = false
        imageViews.forEach { addView(it) }
    }

    fun setContainerSize(widthPx: Int, heightPx: Int) {
        containerWidthPx = widthPx
        containerHeightPx = heightPx
    }

    fun updateImage(position: Int, albumId: Long?, transform: TransformData) {
        if (position !in imageViews.indices) {
            return
        }

        val imageView = imageViews[position]
        if (transform.bitmap == null) {
            imageView.visibility = GONE
            albumIds[position] = Long.MIN_VALUE
            return
        }

        val currentAlbumId = albumId ?: Long.MIN_VALUE
        if (albumIds[position] != currentAlbumId) {
            imageView.setImageBitmap(transform.bitmap)
            albumIds[position] = currentAlbumId
        }

        val width = max((transform.width * transform.modeScaleCompensation).toInt(), 1)
        val height = max((transform.height * transform.modeScaleCompensation).toInt(), 1)

        val params = (imageView.layoutParams as? LayoutParams) ?: LayoutParams(width, height)
        params.width = width
        params.height = height
        params.leftMargin = transform.x.toInt()
        params.topMargin = transform.y.toInt()
        imageView.layoutParams = params

        imageView.pivotX = width * transform.transformOriginX
        imageView.pivotY = height * transform.transformOriginY
        imageView.rotationY = transform.rotationY
        imageView.rotationX = transform.rotationX
        imageView.cameraDistance = transform.cameraDistance
        imageView.scaleX = transform.scaleX
        imageView.scaleY = transform.scaleY
        imageView.translationX = transform.translationX
        imageView.translationY = transform.translationY
        imageView.translationZ = transform.translationZ
        imageView.z = transform.zIndex
        imageView.alpha = transform.alpha
        imageView.visibility = VISIBLE
    }
}

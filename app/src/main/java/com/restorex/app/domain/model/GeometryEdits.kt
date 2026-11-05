package com.restorex.app.domain.model

data class NormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    init { require(left in 0f..<right && right <= 1f && top in 0f..<bottom && bottom <= 1f) }
    val width get() = right - left
    val height get() = bottom - top
    val isFull get() = left == 0f && top == 0f && right == 1f && bottom == 1f
}

data class PerspectiveQuad(
    val topLeft: NormalizedPoint = NormalizedPoint(0f, 0f),
    val topRight: NormalizedPoint = NormalizedPoint(1f, 0f),
    val bottomRight: NormalizedPoint = NormalizedPoint(1f, 1f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0f, 1f),
) {
    val isIdentity get() = this == PerspectiveQuad()
}

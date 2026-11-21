package com.gritnative.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon

val GritShapeNames: List<String> = listOf(
    "Cookie7Sided", "Cookie9Sided", "Cookie12Sided", "Cookie4Sided",
    "Flower", "Sunny", "VerySunny", "SoftBurst", "Burst", "Clover4Leaf",
    "Clover8Leaf", "Heart", "Gem", "Diamond", "Pentagon", "Ghostish"
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun polygonFor(name: String): RoundedPolygon = when (name) {
    "Cookie4Sided" -> MaterialShapes.Cookie4Sided
    "Cookie6Sided" -> MaterialShapes.Cookie6Sided
    "Cookie7Sided" -> MaterialShapes.Cookie7Sided
    "Cookie9Sided" -> MaterialShapes.Cookie9Sided
    "Cookie12Sided" -> MaterialShapes.Cookie12Sided
    "Flower" -> MaterialShapes.Flower
    "Sunny" -> MaterialShapes.Sunny
    "VerySunny" -> MaterialShapes.VerySunny
    "SoftBurst" -> MaterialShapes.SoftBurst
    "Burst" -> MaterialShapes.Burst
    "Clover4Leaf" -> MaterialShapes.Clover4Leaf
    "Clover8Leaf" -> MaterialShapes.Clover8Leaf
    "Heart" -> MaterialShapes.Heart
    "Gem" -> MaterialShapes.Gem
    "Diamond" -> MaterialShapes.Diamond
    "Pentagon" -> MaterialShapes.Pentagon
    "Ghostish" -> MaterialShapes.Ghostish
    else -> MaterialShapes.Cookie7Sided
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun gritShape(name: String, startAngle: Int = 0): Shape {
    val polygon = remember(name) { polygonFor(name) }
    return polygon.toShape(startAngle)
}

package com.miuix.hyperschedule.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/** 用户图标 */
internal val Person: ImageVector by lazy {
    ImageVector.Builder(
        name = "Person",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        group(scaleX = 4f / 3f, scaleY = 4f / 3f, pivotX = 12f, pivotY = 12f) {
            addPath(
                pathData = PathParser().parsePathString(PATH_DATA).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()
}

private const val PATH_DATA =
    "M9.526 10.666Q8.5 9.64 8.5 8.192" +
        "q0 -1.448 1.026 -2.474 1.026 -1.026 2.474 -1.026 1.448 0 2.474 1.026Q15.5 6.744 15.5 8.192" +
        "q0 1.448 -1.026 2.474 -1.026 1.026 -2.474 1.026 -1.448 0 -2.474 -1.026ZM4.5 17.788v-0.704" +
        "q0 -0.734 0.399 -1.361 0.399 -0.626 1.067 -0.963 1.482 -0.727 2.991 -1.09 1.509 -0.363 3.043 -0.363" +
        "t3.043 0.363q1.509 0.363 2.991 1.09 0.667 0.337 1.067 0.963Q19.5 16.35 19.5 17.084v0.704" +
        "q0 0.633 -0.443 1.076 -0.443 0.443 -1.076 0.443H6.019q-0.633 0 -1.076 -0.443" +
        "Q4.5 18.421 4.5 17.788Zm1.5 0.019h12v-0.723" +
        "q0 -0.304 -0.176 -0.562 -0.176 -0.259 -0.478 -0.422 -1.292 -0.637 -2.634 -0.964 -1.341 -0.328 -2.712 -0.328" +
        "t-2.712 0.328Q7.946 15.463 6.654 16.1q-0.302 0.164 -0.478 0.422 -0.176 0.259 -0.176 0.562" +
        "v0.723Zm7.412 -8.203q0.588 -0.588 0.588 -1.413t-0.588 -1.413q-0.588 -0.588 -1.413 -0.588" +
        "t-1.413 0.588q-0.588 0.588 -0.588 1.413t0.588 1.413q0.588 0.588 1.413 0.588t1.413 -0.588Z"

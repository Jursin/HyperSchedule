package com.miuix.hyperschedule.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/** 24dp 画布的图标：[scale] 用于缩小路径，让不同来源的图标视觉重量一致 */
private fun appIcon(
    name: String,
    pathData: String,
    scale: Float = 1f,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        if (scale == 1f) {
            addPath(pathData = nodes, fill = SolidColor(Color.Black))
        } else {
            group(scaleX = scale, scaleY = scale, pivotX = 12f, pivotY = 12f) {
                addPath(pathData = nodes, fill = SolidColor(Color.Black))
            }
        }
    }.build()

/** 书籍图标 */
internal val Book: ImageVector by lazy { appIcon("Book", BookPath) }

private const val BookPath =
"M6 22q-.825 0-1.412-.587T4 20V4q0-.825.588-1.412T6 2h12q.825 0 1.413.588T20 4v16" +
    "q0 .825-.587 1.413T18 22zm0-2h12V4h-2v7l-2.5-1.5L11 11V4H6zm0 0V4zm5-9l2.5-1.5L16 11l-2.5-1.5z"

/** 时钟图标 */
internal val Clock: ImageVector by lazy { appIcon("Clock", ClockPath) }

private const val ClockPath =
"M13 11.6V8q0-.425-.288-.712T12 7t-.712.288T11 8v3.975q0 .2.075.388t.225.337l3.3 3.3" +
    "q.275.275.7.275T16 16t.275-.7t-.275-.7zM12 22q-2.075 0-3.9-.788t-3.175-2.137T2.788 15.9T2 12" +
    "t.788-3.9t2.137-3.175T8.1 2.788T12 2t3.9.788t3.175 2.137T21.213 8.1T22 12t-.788 3.9" +
    "t-2.137 3.175t-3.175 2.138T12 22m0-2q3.325 0 5.663-2.337T20 12t-2.337-5.663T12 4T6.337 6.338" +
    "T4 12t2.338 5.663T12 20"

/** 地点图标 */
internal val Location: ImageVector by lazy { appIcon("Location", LocationPath) }

private const val LocationPath =
"M12 19.35q3.05-2.8 4.525-5.087T18 10.2q0-2.725-1.737-4.462T12 4T7.738 5.738T6 10.2" +
    "q0 1.775 1.475 4.063T12 19.35m-.7 1.85q-.35-.125-.625-.375Q9.05 19.325 7.8 17.9t-2.087-2.762" +
    "t-1.275-2.575T4 10.2q0-3.75 2.413-5.975T12 2t5.588 2.225T20 10.2q0 1.125-.437 2.363" +
    "t-1.275 2.575T16.2 17.9t-2.875 2.925q-.275.25-.625.375t-.7.125t-.7-.125m2.113-9.787" +
    "Q14 10.825 14 10t-.587-1.412T12 8t-1.412.588T10 10t.588 1.413T12 12t1.413-.587"

/** 设备图标 */
internal val Devices: ImageVector by lazy { appIcon("Devices", DevicesPath) }

private const val DevicesPath =
"M11 20H3q-.425 0-.712-.288T2 19t.288-.712T3 18h8q.425 0 .713.288T12 19t-.288.713T11 20" +
    "m-6-3q-.825 0-1.412-.587T3 15V6q0-.825.588-1.412T5 4h15q.425 0 .713.288T21 5t-.288.713T20 6H5v9h6q.425 0 .713.288T12 16t-.288.713T11 17z" +
    " m15 1v-8h-4v8z" +
    "m-4.5 2q-.625 0-1.062-.437T14 18.5v-9q0-.625.438-1.062T15.5 8h5q.625 0 1.063.438T22 9.5v9q0 .625-.437 1.063T20.5 20z" +
    " m2.5-7.5q.325 0 .538-.225t.212-.525q0-.325-.213-.537T18 11q-.3 0-.525.213t-.225.537q0 .3.225.525T18 12.5" +
    "m0 1.5"

/** 云图标 */
internal val Cloud: ImageVector by lazy { appIcon("Cloud", CloudPath, scale = 20f / 22f) }

private const val CloudPath =
"M6.5 20q-2.275 0-3.887-1.575T1 14.575q0-1.95 1.175-3.475T5.25 9.15q.625-2.3 2.5-3.725T12 4" +
    "q2.925 0 4.963 2.038T19 11q1.725.2 2.863 1.488T23 15.5q0 1.875-1.312 3.188T18.5 20zm0-2h12" +
    "q1.05 0 1.775-.725T21 15.5t-.725-1.775T18.5 13H17v-2q0-2.075-1.463-3.538T12 6T8.463 7.463" +
    "T7 11h-.5q-1.45 0-2.475 1.025T3 14.5t1.025 2.475T6.5 18m5.5-6"

/** 学校图标 */
internal val School: ImageVector by lazy { appIcon("School", SchoolPath, scale = 20f / 17.5f) }

private const val SchoolPath =
"M6.05 17.775q-.5-.275-.775-.737T5 16v-4.8L2.6 9.875q-.275-.15-.4-.375T2.075 9" +
    "t.125-.5t.4-.375l8.45-4.6q.225-.125.463-.188T12 3.275t.488.063t.462.187" +
    "l9.525 5.2q.25.125.388.363T23 9.6V16q0 .425-.288.713T22 17t-.712-.288T21 16v-5.9" +
    "l-2 1.1V16q0 .575-.275 1.038t-.775.737l-5 2.7q-.225.125-.462.188t-.488.062" +
    "t-.488-.062t-.462-.188zM12 12.7L18.85 9L12 5.3L5.15 9zm0 6.025l5-2.7V12.25" +
    "l-4.025 2.225q-.225.125-.475.188t-.5.062t-.5-.062t-.475-.188L7 12.25v3.775zm0-3"

/** 星形奖章图标 */
internal val AwardStar: ImageVector by lazy { appIcon("AwardStar", AwardStarPath, scale = 20f / 21f) }

private const val AwardStarPath =
"m12 14.475l1.9 1.15q.275.175.55-.012t.2-.513l-.5-2.175l1.7-1.475q.25-.225.15-.537" +
    "t-.45-.338l-2.225-.175l-.875-2.05q-.125-.3-.45-.3t-.45.3l-.875 2.05l-2.225.175" +
    "q-.35.025-.45.338t.15.537l1.7 1.475l-.5 2.175q-.075.325.2.513t.55.012zM8.65 20H6" +
    "q-.825 0-1.412-.587T4 18v-2.65L2.075 13.4q-.275-.3-.425-.662T1.5 12t.15-.737t.425-.663" +
    "L4 8.65V6q0-.825.588-1.412T6 4h2.65l1.95-1.925q.3-.275.663-.425T12 1.5t.738.15t.662.425" +
    "L15.35 4H18q.825 0 1.413.588T20 6v2.65l1.925 1.95q.275.3.425.663t.15.737t-.15.738" +
    "t-.425.662L20 15.35V18q0 .825-.587 1.413T18 20h-2.65l-1.95 1.925q-.3.275-.662.425" +
    "T12 22.5t-.737-.15t-.663-.425zm.85-2l2.5 2.5l2.5-2.5H18v-3.5l2.5-2.5L18 9.5V6h-3.5" +
    "L12 3.5L9.5 6H6v3.5L3.5 12L6 14.5V18zm2.5-6"

/** 用户图标 */
internal val Person: ImageVector by lazy { appIcon("Person", PersonPath, scale = 4f / 3f) }

private const val PersonPath =
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

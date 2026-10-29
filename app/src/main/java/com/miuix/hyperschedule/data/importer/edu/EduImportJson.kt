package com.miuix.hyperschedule.data.importer.edu

import com.miuix.hyperschedule.data.importer.ComboScheduleJson
import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableConfigJson
import com.miuix.hyperschedule.data.importer.TimeSlotJson
import kotlinx.serialization.json.Json

/** 教务脚本回传的四类负载，字段与课程表 JSON 导入的 DTO 一致 */
object EduImportJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    fun courses(text: String): List<CourseJson> = json.decodeFromString<List<CourseJson>>(text)

    fun timeSlots(text: String): List<TimeSlotJson> = json.decodeFromString<List<TimeSlotJson>>(text)

    fun config(text: String): CourseTableConfigJson = json.decodeFromString<CourseTableConfigJson>(text)

    fun combo(text: String): ComboScheduleJson = json.decodeFromString<ComboScheduleJson>(text)

    fun strings(text: String): List<String> = json.decodeFromString<List<String>>(text)
}

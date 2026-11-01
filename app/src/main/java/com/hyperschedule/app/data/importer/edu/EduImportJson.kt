package com.hyperschedule.app.data.importer.edu

import com.hyperschedule.app.data.importer.ComboScheduleJson
import com.hyperschedule.app.data.importer.CourseJson
import com.hyperschedule.app.data.importer.CourseTableConfigJson
import com.hyperschedule.app.data.importer.TimeSlotJson
import kotlinx.serialization.json.Json

/** 教务脚本回传的四类负载，字段沿用课程表 JSON 导入的 DTO */
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

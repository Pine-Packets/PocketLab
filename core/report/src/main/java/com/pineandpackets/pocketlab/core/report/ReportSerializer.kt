package com.pineandpackets.pocketlab.core.report

import com.pineandpackets.pocketlab.core.model.AnalysisReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReportSerializer {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    fun serialize(report: AnalysisReport): String {
        return json.encodeToString(report)
    }
    
    fun deserialize(jsonString: String): AnalysisReport {
        return json.decodeFromString(jsonString)
    }
}

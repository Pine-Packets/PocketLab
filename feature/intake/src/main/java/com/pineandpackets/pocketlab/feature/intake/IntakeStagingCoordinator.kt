package com.pineandpackets.pocketlab.feature.intake

import android.net.Uri
import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.io.CaseWorkspace
import com.pineandpackets.pocketlab.core.io.FileStager
import com.pineandpackets.pocketlab.engine.archive.SplitApkSetBuilder
import java.io.File

data class StagedCase(
    val caseId: String,
    val uriCount: Int,
    val bundledIntoApks: Boolean
)

/**
 * Stages one or more user-selected URIs into a private case workspace.
 *
 * A single file is staged as the case's original input. Multiple files are
 * staged into the case scratch directory and bundled into a synthetic APKS
 * container (via [SplitApkSetBuilder]) so the package-set analysis path can
 * merge and compare split APKs (FR-IN-003).
 *
 * Security: inputs are never executed; bundled bytes are copied verbatim.
 * Any failure deletes the whole case workspace (including partial stages).
 */
class IntakeStagingCoordinator(
    private val stager: FileStager,
    private val workspace: CaseWorkspace,
    private val builder: SplitApkSetBuilder = SplitApkSetBuilder()
) {

    suspend fun stage(uris: List<Uri>, caseId: String): Result<StagedCase> {
        if (uris.isEmpty()) {
            return Result.failure(AnalysisError.IntakeError("No files selected"))
        }

        if (uris.size == 1) {
            return stager.stageFile(uris[0], caseId).map {
                StagedCase(caseId = caseId, uriCount = 1, bundledIntoApks = false)
            }
        }

        val scratchDir = workspace.getScratchDir(caseId)
        val stagedParts = mutableListOf<File>()
        try {
            uris.forEachIndexed { index, uri ->
                val dest = File(scratchDir, "part_$index.apk")
                val result = stager.stageFileTo(uri, dest)
                if (result.isFailure) {
                    throw result.exceptionOrNull()
                        ?: AnalysisError.IntakeError("Failed to stage file ${index + 1}")
                }
                stagedParts.add(dest)
            }

            val buildResult = builder.build(
                apkFiles = stagedParts,
                outputDir = workspace.getCaseDir(caseId),
                containerName = "original.bin"
            )
            if (buildResult.isFailure) {
                throw buildResult.exceptionOrNull()
                    ?: AnalysisError.ArchiveError("Failed to build split APK set")
            }

            return Result.success(
                StagedCase(caseId = caseId, uriCount = uris.size, bundledIntoApks = true)
            )
        } catch (e: Exception) {
            workspace.deleteCaseWorkspace(caseId)
            return Result.failure(
                if (e is AnalysisError) e
                else AnalysisError.IntakeError("Failed to stage files", e)
            )
        } finally {
            stagedParts.forEach { it.delete() }
            scratchDir.delete()
        }
    }
}

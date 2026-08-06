package com.pineandpackets.pocketlab.engine.service.aidl;

import android.os.ParcelFileDescriptor;
import com.pineandpackets.pocketlab.engine.service.aidl.IAnalyzerCallback;

interface IAnalyzerService {
    String startAnalysis(
        in String requestJson,
        in ParcelFileDescriptor inputFd,
        in ParcelFileDescriptor outputFd,
        in IAnalyzerCallback callback
    );

    void cancelAnalysis(in String jobId);

    String getEngineInfo();

    boolean isAnalysisRunning(in String jobId);
}

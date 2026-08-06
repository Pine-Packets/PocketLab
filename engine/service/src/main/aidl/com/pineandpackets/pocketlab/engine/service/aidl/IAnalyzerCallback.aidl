package com.pineandpackets.pocketlab.engine.service.aidl;

interface IAnalyzerCallback {
    void onProgressUpdate(in String jobId, in String progressJson);
    void onAnalysisComplete(in String jobId, in String reportJson);
    void onAnalysisError(in String jobId, in String errorCode, in String message);
}

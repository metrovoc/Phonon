package com.metrovoc.phonon.server;

/** Outcome of storing audio: a failure, or success with the title the source reports, if any. */
public record DownloadResult(AudioFailure failure, String title) {

    public static DownloadResult ok(String title) {
        return new DownloadResult(null, title);
    }

    public static DownloadResult failed(AudioFailure failure) {
        return new DownloadResult(failure, "");
    }

    public boolean success() {
        return failure == null;
    }
}

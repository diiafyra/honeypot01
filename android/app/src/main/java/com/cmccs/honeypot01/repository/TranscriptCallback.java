package cmc.cs.honeypot01.repository;

public interface TranscriptCallback {
    void onSuccess(String transcript);
    void onError(Exception e);
}
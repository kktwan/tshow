package com.t.tshow.domain.ingest.dto;


/** 한 번의 수집 실행에서 센 건수 */
public class IngestCounts {

    private int fetched;
    private int inserted;
    private int updated;
    private int skipped;
    private int failed;

    public void recordFetched(boolean inserted) {
        fetched++;
        if (inserted) this.inserted++;
        else this.updated++;
    }

    public void recordSkipped() {
        skipped++;
    }

    public void recordFailed() {
        failed++;
    }

    public int fetched() {
        return fetched;
    }

    public int inserted() {
        return inserted;
    }

    public int updated() {
        return updated;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    @Override
    public String toString() {
        return "가져옴 " + fetched + " (신규 " + inserted + ", 갱신 " + updated + "), 건너뜀 " + skipped + ", 실패 " + failed;
    }
}

package com.t.tshow.ingest;

import com.t.tshow.global.config.IngestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** 정기 수집(cron)과, 설정을 켰을 때 앱 시작 직후의 수집 한 번. 수집이 겹쳐 돌지 않게 막는다. */
@Component
public class IngestScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    private final IngestService ingest;
    private final IngestProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public IngestScheduler(IngestService ingest, IngestProperties properties) {
        this.ingest = ingest;
        this.properties = properties;
    }

    @Scheduled(cron = "${tshow.ingest.cron:0 0 3 * * *}", zone = "${tshow.ingest.zone:Asia/Seoul}")
    public void scheduled() {
        runOnce();
    }

    /** 로컬 확인용: tshow.ingest.on-startup=true 이면 앱 시작 직후 별도 스레드에서 한 번 실행한다 */
    @Override
    public void run(ApplicationArguments args) {
        if (properties.onStartup()) {
            Thread thread = new Thread(this::runOnce, "ingest-startup");
            thread.start();
        }
    }

    private void runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.info("이미 수집이 진행 중이라 건너뜀");
            return;
        }
        try {
            ingest.runAll();
        } finally {
            running.set(false);
        }
    }
}

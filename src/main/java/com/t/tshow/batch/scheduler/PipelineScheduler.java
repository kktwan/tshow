package com.t.tshow.batch.scheduler;

import com.t.tshow.batch.service.DataPipelineService;
import com.t.tshow.global.config.IngestProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 정기 실행(cron)과, 설정을 켰을 때 앱 시작 직후의 한 번 실행 */
@Component
public class PipelineScheduler implements ApplicationRunner {

    private final DataPipelineService pipeline;
    private final IngestProperties properties;

    public PipelineScheduler(DataPipelineService pipeline, IngestProperties properties) {
        this.pipeline = pipeline;
        this.properties = properties;
    }

    @Scheduled(cron = "${tshow.ingest.cron:0 0 3 * * *}", zone = "${tshow.ingest.zone:Asia/Seoul}")
    public void scheduled() {
        pipeline.runOnce();
    }

    /** 로컬 확인용: tshow.ingest.on-startup=true 이면 앱 시작 직후 별도 스레드에서 한 번 실행한다 */
    @Override
    public void run(ApplicationArguments args) {
        if (properties.onStartup()) {
            new Thread(pipeline::runOnce, "pipeline-startup").start();
        }
    }
}

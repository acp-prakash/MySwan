package org.myswan.model.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "schedulerConfig")
public class SchedulerConfig {

    @Id
    private String id = "SCHEDULER_CONFIG";

    /** Whether the scheduler is active */
    private boolean enabled = false;

    /** Interval in minutes between runs */
    private int intervalMinutes = 30;

    /** Window start hour (24h, inclusive) – default 8 = 8:00 AM */
    private int windowStartHour = 8;

    /** Window end hour (24h, inclusive) – default 18 = 6:00 PM */
    private int windowEndHour = 18;

    /** Name of the step that last failed (null if last run was OK) */
    private String lastFailedStep;

    /** Error message from the last failed step (null if last run was OK) */
    private String lastFailedError;

    /** ISO timestamp of the last failed run */
    private String lastFailedAt;

    /** ISO timestamp of last successful full run */
    private String lastSuccessAt;

    /** true while the pipeline is actively executing */
    private boolean running = false;

    /** ISO timestamp when the current/last run started */
    private String lastRunStartedAt;

    /** Per-step progress: written live as each step starts/ends */
    private List<StepStatus> stepStatuses = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepStatus {
        private String name;
        /** PENDING | RUNNING | SUCCESS | FAILED */
        private String status;
        private String startedAt;
        private String finishedAt;
        private String error;
    }
}

package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Score {
    private String signal;
    private int signalDays;
    private String signalReason;
    private int overallScore;
    private int dayTradingScore;
    private int swingTradingScore;
    private int reversalScore;
    private int breakoutScore;
    private int patternScore;
    private String overallReason;
    private String dayTradingReason;
    private String swingTradingReason;
    private String reversalReason;
    private String breakoutReason;
    private String patternReason;
}
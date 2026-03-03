package org.myswan.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Main response DTO for prediction analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionAnalysisResponse {

    private String analysisDate;           // When analysis was run
    private int totalRecordsAnalyzed;      // Total stock history records
    private String bestOverallTimeHorizon; // e.g., "Day +3"
    private String bestOverallCombo;       // Best combo across all days
    private double bestWinRate;            // Best win percentage found
    private double bestAvgReturn;          // Best average return found

    // Peak analysis - combos with highest returns achieved on ANY day (D1-D10)
    private String bestPeakCombo;          // Combo with highest peak return
    private double bestPeakReturn;         // Highest peak return achieved
    private String bestPeakDay;            // Which day achieved the peak

    // Analysis by time horizon (D1-D10)
    private List<TimeHorizonAnalysis> timeHorizonResults = new ArrayList<>();

    // Top 10 combos for each time horizon
    private List<DetailedComboAnalysis> detailedResults = new ArrayList<>();

    // Peak analysis - Top combos by maximum return achieved on any day
    private List<PeakAnalysis> peakAnalysisResults = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeakAnalysis {
        private String combo;
        private double peakReturn;         // Max return achieved on any day (D1-D10)
        private String peakDay;            // Which day achieved the peak
        private double avgPeakReturn;      // Average of peak returns across all occurrences
        private int occurrences;           // How many times this combo occurred
        private double winRate;            // What % achieved positive peak
        private double reliabilityScore;   // Composite score: winRate * log10(occurrences)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailedComboAnalysis {
        private String timeHorizon;
        private List<ComboAnalysis> topCombos = new ArrayList<>();
    }
}


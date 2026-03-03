package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Layer 1: Pre-Spike Setup Gate Signal (GPT-4 Hybrid System)
 * Controls WHEN to look - Prevents chasing, forces discipline
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GateSignal {

    // Overall gate status
    private boolean gatePass;           // true if Day 0 or Day 1 criteria met
    private String gateType;            // "DAY_0_SETUP" or "DAY_1_ENTRY" or "NO_GATE"
    private int gateScore;              // 0-100 score for gate strength

    // Day 0 Setup criteria (Watchlist identification)
    private boolean day0RsiCheck;       // RSI 20-35
    private boolean day0VolumeCheck;    // Volume ≥ 2× average
    private boolean day0AtrCheck;       // ATR compression < 0.6
    private boolean day0BottomCheck;    // Bottom conditions ≥ 4
    private boolean day0PriceCheck;     // Price change ≤ +5%
    private boolean day0DownDaysCheck;  // downDays ≥ 2
    private boolean day0LiquidityCheck; // price > $0.50, volume > 100k
    private boolean day0UpDaysCheck;    // upDays ≤ 1

    // Day 1 Entry criteria (Safe entry confirmation)
    private boolean day1HigherLow;      // Higher low vs yesterday
    private boolean day1Consolidation;  // Inside candle or tight range
    private boolean day1VolumeCooling;  // Volume ≤ yesterday
    private boolean day1NotExtended;    // Price < +10% from Day 0 low
    private boolean day1NoFomoGap;      // Gap < 15%
    private boolean day1NotLate;        // upDays ≤ 2

    // Metadata
    private String reason;              // Human-readable explanation
}


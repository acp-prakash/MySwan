package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MomentumPopSignal {
    private boolean isMomentumPop;
    private int popScore;        // 0–100
    private String popType;      // “Squeeze”, “TrendBreakout”, “MicroBase”
    private List<String> reasons = new ArrayList<>();
}
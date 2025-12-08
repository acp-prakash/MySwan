package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyRank {
    private double finalRank;
    private int safetyRank;
    private double pickScore;
    private double allocation;
}
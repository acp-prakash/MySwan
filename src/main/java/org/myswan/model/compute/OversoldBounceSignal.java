package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OversoldBounceSignal {
    private boolean isOversoldBounce;
    private int bounceScore;              // 0–100
    private String bounceType;            // "None", "Oversold", "Deep Oversold", "Explosive Bounce"
    private List<String> reasons = new ArrayList<>();    
}
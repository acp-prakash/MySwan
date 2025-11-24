package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BottomSignal {
    private boolean isBottom;
    private int conditionsMet;
    private String strength;   // "None", "Weak", "Strong Reversal", "Mega Bounce"
    private List<String> reasons = new ArrayList<>();
}
package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpikeSignal {
    private int spikeScore;
    private String spikeType;
    private boolean spikeLikely;
    private List<String> reasons = new ArrayList<>();
}
package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricsInfo {
    private String ticker;
    private List<String> day0Factors;
    private MetricsDay d1;
    private MetricsDay d2;
    private MetricsDay d3;
    private MetricsDay d4;
    private MetricsDay d5;
    private MetricsDay d6;
    private MetricsDay d7;
    private MetricsDay d8;
    private MetricsDay d9;
    private MetricsDay d10;
}
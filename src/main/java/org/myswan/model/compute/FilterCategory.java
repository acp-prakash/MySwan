package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterCategory {
    private String primaryCategory;
    private List<String> category = new ArrayList<>();
    private List<String> criteria = new ArrayList<>();
}
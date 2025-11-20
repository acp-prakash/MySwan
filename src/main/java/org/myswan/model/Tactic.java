package org.myswan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tactic")
public class Tactic {
    @Id
    private String id;
    private String name;
    private int rank;
    private double successRatio;
    private double profitLoss;
    private int attempts;
    private LocalDate dateTried;
    private LocalDate addedDate;
}

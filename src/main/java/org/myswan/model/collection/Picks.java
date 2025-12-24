package org.myswan.model.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "picks")
public class Picks {
    @Id
    private String id;
    private String ticker;
    private String reason;
    private LocalDate addedDate;
    private double addedPrice;
    private double entry;
    private double target;
    private double stopLoss;
    private LocalDate targetDate;
    private LocalDate targetMetDate;
    private LocalDate stopLossMetDate;
    private boolean targetMet;
    private boolean stopLossMet;
    private String historyDate;
    private Stock stock;
    private String status;// e.g., "OPEN", "CLOSED"
    private double max;
    private double min;
}

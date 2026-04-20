package org.myswan.model.collection;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private LocalDate addedDate;
    private double addedPrice;
    private double entry;
    private double target;
    private double stopLoss;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private LocalDate targetDate;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private LocalDate targetMetDate;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private LocalDate stopLossMetDate;
    private boolean targetMet;
    private boolean stopLossMet;
    private String historyDate;
    private Stock stock;
    private String status;// e.g., "OPEN", "CLOSED"
    private double max;
    private double min;
    private String monitor = "N";
}
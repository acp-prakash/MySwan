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
@Document(collection = "options")
public class Options {
    @Id
    private String id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate histDate;
    private String ticker;
    private String optionId;
    private String optionName;
    private String type;
    private double priceOnAdd;
    private double stockPriceOnAdd;
    private double stockPriceCurrent;
    private double change;
    private double changeSinceAdded;
    private double price;
    private double high;
    private double low;
    private int volume;
    private int openInterest;
    private double delta;
    private double gamma;
    private double theta;
    private double iv;
    private double vega;
    private int daysUpDown;
    private Stock stock;
}
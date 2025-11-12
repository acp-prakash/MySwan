package org.myswan.model;

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
@Document(collection = "stock")
public class Stock {
    @Id
    private String id;
    private String ticker;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate histDate;
    private Rating rating;
    private double price;
    private double open;
    private double high;
    private double low;
    private double change;
    private double volume;
    private double prevClose;
    private double priceChg5D;
    private double priceChg10D;
    private double priceChg20D;
    private double low52;
    private double high52;
    private double sma20;
    private double sma50;
    private double sma100;
    private double sma200;
    private boolean hasPattern;
    private int upDays;
    private int downDays;
    private double downLow;
    private double upHigh;
    private LocalDate earningsDate;
    private int earningDays;
}
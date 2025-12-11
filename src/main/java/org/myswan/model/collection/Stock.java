package org.myswan.model.collection;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.myswan.model.compute.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock")
public class Stock {
    @Id
    private String id;
    private String type;
    private String ticker;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate histDate;
    private Rating rating;
    private Score score;
    private BottomSignal bottom;
    private SpikeSignal spike;
    private OversoldBounceSignal oversold;
    private FilterCategory filterCategory;
    private MomentumPopSignal momPop;
    private DailyRank dailyRank;
    private double price;
    private double open;
    private double high;
    private double low;
    private double change;
    private double prevClose;
    private double priceChg5D;
    private double priceChg10D;
    private double priceChg20D;
    private double low52;
    private double high52;
    private boolean hasPattern;
    private int upDays;
    private int downDays;
    private double downLow;
    private double upHigh;
    private LocalDate earningsDate;
    private int earningDays;
    private double sma9;
    private double sma20;
    private double sma21;
    private double sma50;
    private double sma100;
    private double sma200;
    private double ema9;
    private double ema20;
    private double ema21;
    private double ema50;
    private double ema100;
    private double ema200;
    private double macd1226;
    private double rsi14;
    private double atr14;
    private double momentum;
    private double volume;
    private double volumeChange;
    private double avgVolume10D;
    private double vwap;
    private int noOfLongPatterns;
    private int noOfShortPatterns;
    private String optionPref;

    // Transient field - not persisted to DB, populated at runtime for API responses
    @Transient
    private List<Pattern> patterns;
}
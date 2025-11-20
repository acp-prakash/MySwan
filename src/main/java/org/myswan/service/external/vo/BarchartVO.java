package org.myswan.service.external.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BarchartVO {

    @JsonProperty("symbol")
    private String symbol;

    @JsonAlias({"lastPrice", "dailyLastPrice"})
    private double price;

    @JsonAlias({"dailyOpenPrice", "openPrice"})
    private double open;

    @JsonAlias({"dailyHighPrice", "highPrice"})
    private double high;

    @JsonAlias({"dailyLowPrice", "lowPrice"})
    private double low;

    @JsonAlias({"dailyPriceChange", "priceChange"})
    private double change;


    @JsonAlias({"dailyVolume", "volume"})
    private long volume;

    @JsonProperty("epsDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate epsDate;

    @JsonProperty("nextEarningsDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextEarningsDate;

    @JsonProperty("averageRecommendation")
    private double averageRecommendation;

    @JsonProperty("totalRecommendations")
    private int totalRecommendations;

    @JsonProperty("symbolName")
    private String symbolName;

    @JsonAlias({"dailyOpinionShortTerm", "opinionShortTerm"})
    private String opinionShortTerm;   // 50

    @JsonAlias({"dailyOpinionLongTerm", "opinionLongTerm"})
    private String opinionLongTerm;    // 100

    @JsonProperty("symbolCode")
    private String symbolCode;           // "STK"

    @JsonProperty("symbolType")
    private int symbolType;

    @JsonAlias({"dailyOpinion", "opinion"})
    private String opinion;            // 80

    @JsonAlias({"dailyPreviousPrice", "previousPrice"})
    private double previousPrice;

    @JsonAlias({"dailyPriceChange5d", "priceChange5d"})
    private double priceChange5d;

    @JsonAlias({"dailyPriceChange10d", "priceChange10d"})
    private double priceChange10d;

    @JsonAlias({"dailyPriceChange20d", "priceChange20d"})
    private double priceChange20d;

    @JsonAlias({"dailyHighPrice5d", "highPrice5d"})
    private double highPrice5d;

    @JsonAlias({"dailyLowPrice5d", "lowPrice5d"})
    private double lowPrice5d;

    @JsonAlias({"dailyTrendSpotterSignal", "trendSpotterSignal"})
    private String trendSpotterSignal;
}
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

    @JsonProperty("dailyLastPrice") @JsonAlias("lastPrice")
    private double price;

    @JsonProperty("dailyOpenPrice") @JsonAlias("openPrice")
    private double open;

    @JsonProperty("dailyHighPrice") @JsonAlias("highPrice")
    private double high;

    @JsonProperty("dailyLowPrice") @JsonAlias("lowPrice")
    private double low;

    @JsonProperty("dailyPriceChange") @JsonAlias("priceChange")
    private double change;

    @JsonProperty("dailyVolume") @JsonAlias("volume")
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

    @JsonProperty("dailyOpinionShortTerm") @JsonAlias("opinionShortTerm")
    private String opinionShortTerm;   // 50

    @JsonProperty("dailyOpinionLongTerm") @JsonAlias("opinionLongTerm")
    private String opinionLongTerm;    // 100

    @JsonProperty("symbolCode")
    private String symbolCode;           // "STK"

    @JsonProperty("symbolType")
    private int symbolType;

    @JsonProperty("dailyOpinion") @JsonAlias("opinion")
    private String opinion;            // 80

    @JsonProperty("dailyPreviousPrice") @JsonAlias("previousPrice")
    private double previousPrice;

    @JsonProperty("dailyPriceChange5d") @JsonAlias("priceChange5d")
    private double priceChange5d;

    @JsonProperty("dailyPriceChange10d") @JsonAlias("priceChange10d")
    private double priceChange10d;

    @JsonProperty("dailyPriceChange20d") @JsonAlias("priceChange20d")
    private double priceChange20d;

    @JsonProperty("dailyHighPrice5d") @JsonAlias("highPrice5d")
    private double highPrice5d;

    @JsonProperty("dailyLowPrice5d") @JsonAlias("lowPrice5d")
    private double lowPrice5d;

    @JsonProperty("dailyTrendSpotterSignal") @JsonAlias("trendSpotterSignal")
    private String trendSpotterSignal;

    @JsonProperty("lastPrice")
    private double lastPrice;
}
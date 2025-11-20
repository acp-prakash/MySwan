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
public class TradingViewVO {
    private String type;
    private String ticker;
    private double price;
    private double open;
    private double high;
    private double low;
    private double change;
    private String techRating;
    private String maRating;
    private String analystsRating;
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
}
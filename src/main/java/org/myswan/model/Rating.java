package org.myswan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rating {
    private int btAnalysts;
    private double btAnalystRating;
    private String btShortRating;
    private String btLongRating;
    private String btRating;
    private String btTrend;
    private String zacksRank;
    private String zacksRating;
    private String siusScore;
    private String siusRating;
    private int siusDays;
    private String tipRating;
    private String tipBuyHoldSell;
    private String tradingViewTechRating;
    private String tradingViewAnalystsRating;
    private String tradingViewMARating;
    private String tradingViewOSRating;
    private String tradingViewBullBearPower;
    private String tickeronRating;
    private String tickeronRatingAt;
    private String tickeronRatingOn;
    private String tickeronAIRating;
    private String tickeronUnderOver;
}
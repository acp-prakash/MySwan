package org.myswan.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TickerGroupDTO {
    private String mainTicker;
    private String mainName;
    private String mainType;
    private double mainPrice;
    private double mainChange;
    private double mainHigh;
    private double mainLow;
    private double mainVolume;
    private List<RelatedTickerDTO> relatedTickers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedTickerDTO {
        private String ticker;
        private String name;
        private String type;
        private double price;
        private double change;
        private double high;
        private double low;
        private double volume;
        private String leverageType; // "Long" or "Short"
    }
}


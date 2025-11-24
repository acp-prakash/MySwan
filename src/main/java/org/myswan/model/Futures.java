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
@Document(collection = "futures")
public class Futures {
    @Id
    private String id;
    private String type;
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
    private double openInterest;
    private String expiryDate;
    private int expiryDays;
    private double prevClose;
    private int upDays;
    private int downDays;
    private double downLow;
    private double upHigh;
}
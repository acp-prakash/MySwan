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
@Document(collection = "appCache")
public class AppCache {
    @Id
    private String id = "APP_CACHE";
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate updatedAt;
    private String barchartToken;
    private String barchartCookie;
    private String etradeToken;
    private String etradeCookie;
    private String tradingViewToken;
    private String tradingViewCookie;
}
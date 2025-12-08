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
@Document(collection = "master")
public class Master {
    @Id
    private String id;
    private String ticker;
    private String name;
    private String type;
    private double addedPrice;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate addedDate;
    private boolean etradePatternLookup; // Default to false (disabled) - explicitly enable in DB
}

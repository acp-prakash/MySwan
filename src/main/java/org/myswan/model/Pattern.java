package org.myswan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pattern")
public class Pattern {
    @Id
    private String id;
    private String ticker;
    private Date date;
}

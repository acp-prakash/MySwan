package org.myswan.controller.external;

import org.myswan.model.collection.Futures;
import org.myswan.service.external.BarchartClient;
import org.myswan.service.external.FuturesBarchartClient;
import org.myswan.service.external.vo.BarchartVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/external")
public class BarchartController {

    private final BarchartClient barchartClient;
    private final FuturesBarchartClient futuresBarchartClient;

    public BarchartController(BarchartClient barchartClient, FuturesBarchartClient futuresBarchartClient){
        this.barchartClient = barchartClient;
        this.futuresBarchartClient = futuresBarchartClient;
    }

    @PostMapping("/getDailyQuotes")
    public ResponseEntity<List<BarchartVO>> getDailyQuotes() throws Exception {
        List<BarchartVO> result = barchartClient.getDailyQuotes();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/getIntraDayQuotes")
    public ResponseEntity<List<BarchartVO>> getIntraDayQuotes() throws Exception {
        List<BarchartVO> result = barchartClient.getIntraDayQuotes();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/futures/fetch-barchart")
    public ResponseEntity<List<Futures>> fetchBarchartData() {
        try {
            List<Futures> futures = futuresBarchartClient.fetchAllFutures();
            return ResponseEntity.ok(futures);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

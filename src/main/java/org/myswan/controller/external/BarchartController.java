package org.myswan.controller.external;

import org.myswan.service.external.BarchartClient;
import org.myswan.service.external.vo.BarchartVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/external")
public class BarchartController {

    private final BarchartClient barchartClient;

    public BarchartController(BarchartClient barchartClient){
        this.barchartClient = barchartClient;
    }

    @GetMapping("/getDailyQuotes")
    public ResponseEntity<List<BarchartVO>> getDailyQuotes() throws Exception {
        List<BarchartVO> result = barchartClient.getDailyQuotes();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getIntraDayQuotes")
    public ResponseEntity<List<BarchartVO>> getIntraDayQuotes() throws Exception {
        List<BarchartVO> result = barchartClient.getIntraDayQuotes();
        return ResponseEntity.ok(result);
    }
}

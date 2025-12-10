package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SyncupService {

    private final FuturesService futuresService;
    private final StockService stockService;
    private final PatternService patternService;
    private final PicksService picksService;
    private final GuaranteedExplosiveService guaranteedExplosiveService;

    public SyncupService(FuturesService futuresService, StockService stockService,
                         PatternService patternService, PicksService picksService,
                         GuaranteedExplosiveService guaranteedExplosiveService) {
        this.futuresService = futuresService;
        this.stockService = stockService;
        this.patternService = patternService;
        this.picksService = picksService;
        this.guaranteedExplosiveService = guaranteedExplosiveService;
    }

    public String syncupAllHistory() {
        try {
            log.info("Syncing all futures history data...");
            futuresService.syncFuturesHistory();
            stockService.syncStockHistory();
            patternService.syncPatternHistory();
            picksService.syncPicksHistory();
            //guaranteedExplosiveService.syncGuaranteedPicksHistory();
            log.info("All Futures history data sync completed.");
            return "SUCCESS";
        }
        catch(Exception ex)
        {
            log.error("All Futures history data sync failed.", ex);
            return "FAILURE";
        }
    }

    public String syncupFutureHistory() {
        try {
            log.info("Syncing futures history data...");
            futuresService.syncFuturesHistory();
            log.info("Futures history data sync completed.");
            return "SUCCESS";
        }
        catch(Exception ex)
        {
            log.error("Futures history data sync failed.", ex);
            return "FAILURE";
        }
    }

    public String syncupStockHistory() {
        try {
            log.info("Syncing Stocks history data...");
            stockService.syncStockHistory();
            log.info("Stocks history data sync completed.");
            return "SUCCESS";
        }
        catch(Exception ex)
        {
            log.error("Stocks history data sync failed.", ex);
            return "FAILURE";
        }
    }

    public String syncupPatternHistory() {
        try {
            log.info("Syncing Patterns history data...");
            patternService.syncPatternHistory();
            log.info("Patterns history data sync completed.");
            return "SUCCESS";
        }
        catch(Exception ex)
        {
            log.error("Patterns history data sync failed.", ex);
            return "FAILURE";
        }
    }

    public String syncPicksHistory() {
        try {
            picksService.syncPicksHistory();
            return "SUCCESS";
        }
        catch(Exception ex)
        {
            log.error("Picks history data sync failed.", ex);
            return "FAILURE";
        }
    }

    public String syncGuaranteedPicksHistory() {
        try {
            log.info("Syncing GuaranteedPicks history data...");
            //guaranteedExplosiveService.syncGuaranteedPicksHistory();
            log.info("GuaranteedPicks history data sync completed.");
            return "SUCCESS";
        }
        catch(Exception ex)
        {
            log.error("GuaranteedPicks history data sync failed.", ex);
            return "FAILURE";
        }
    }

}

package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Watchlist;
import org.myswan.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;

    public WatchlistService(WatchlistRepository watchlistRepository) {
        this.watchlistRepository = watchlistRepository;
    }

    public List<Watchlist> list() {
        return watchlistRepository.findAll();
    }

    public List<String> getTickers() {
        return watchlistRepository.findAll().stream()
                .map(Watchlist::getTicker)
                .collect(Collectors.toList());
    }

    public Watchlist add(String ticker) {
        if (watchlistRepository.existsByTicker(ticker)) {
            log.warn("Ticker {} already exists in watchlist", ticker);
            throw new IllegalArgumentException("Ticker already in watchlist: " + ticker);
        }
        Watchlist watchlist = new Watchlist();
        watchlist.setTicker(ticker.toUpperCase());
        log.info("Adding ticker {} to watchlist", ticker);
        return watchlistRepository.save(watchlist);
    }

    @Transactional
    public void delete(String ticker) {
        log.info("Removing ticker {} from watchlist", ticker);
        watchlistRepository.deleteByTicker(ticker);
    }

    public boolean exists(String ticker) {
        return watchlistRepository.existsByTicker(ticker);
    }
}


package com.routechain.data.port;

import com.routechain.data.model.QuoteRecord;

import java.util.Optional;

public interface QuoteRepository {
    void storeQuote(QuoteRecord quote);
    Optional<QuoteRecord> quoteById(String quoteId);
}

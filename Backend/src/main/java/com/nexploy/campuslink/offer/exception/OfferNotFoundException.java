package com.nexploy.campuslink.offer.exception;

public class OfferNotFoundException extends RuntimeException {

    public OfferNotFoundException(String offerId) {
        super("Offer not found: " + offerId);
    }
}

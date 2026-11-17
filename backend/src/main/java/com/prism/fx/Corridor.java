package com.prism.fx;

import java.math.BigDecimal;

/**
 * A spend corridor: the purchase currency plus its mid-market rate to USD (the funding currency).
 */
public enum Corridor {
    MXN("0.054", "Mexico"),
    BRL("0.185", "Brazil"),
    COP("0.00025", "Colombia"),
    EUR("1.08", "Germany"),
    GBP("1.27", "United Kingdom");

    private final BigDecimal usdMidRate;
    private final String country;

    Corridor(String usdMidRate, String country) {
        this.usdMidRate = new BigDecimal(usdMidRate);
        this.country = country;
    }

    public BigDecimal usdMidRate() {
        return usdMidRate;
    }

    public String country() {
        return country;
    }
}

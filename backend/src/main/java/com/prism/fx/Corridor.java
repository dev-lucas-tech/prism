package com.prism.fx;

import java.math.BigDecimal;

/**
 * A spend corridor: the purchase currency plus its mid-market rate to USD (the funding currency).
 */
public enum Corridor {
    MXN("0.054"),
    BRL("0.185"),
    COP("0.00025"),
    EUR("1.08"),
    GBP("1.27");

    private final BigDecimal usdMidRate;

    Corridor(String usdMidRate) {
        this.usdMidRate = new BigDecimal(usdMidRate);
    }

    public BigDecimal usdMidRate() {
        return usdMidRate;
    }
}

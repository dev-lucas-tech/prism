package com.prism.fx;

import org.springframework.stereotype.Service;

/**
 * Jeeves-style FX markup per corridor (bps over mid), and the stablecoin-corridor fee.
 * LATAM corridors carry a wider spread than EUR/GBP.
 */
@Service
public class MarkupPolicy {

    public int markupBps(Corridor corridor) {
        return switch (corridor) {
            case MXN -> 250;
            case BRL -> 300;
            case COP -> 350;
            case EUR -> 120;
            case GBP -> 120;
        };
    }

    public int stablecoinFeeBps() {
        return 50;
    }
}

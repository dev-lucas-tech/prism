package com.prism.analyst.domain;

import java.math.BigDecimal;
import java.util.List;

public record AnalystReport(String headline, BigDecimal totalAddressable, List<Finding> findings) {

    public record Finding(
            String title,
            String severity,
            BigDecimal dollarsAtStake,
            String corridor,
            List<String> evidenceTxIds,
            String recommendation) {
    }
}

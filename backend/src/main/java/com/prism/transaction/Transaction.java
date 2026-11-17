package com.prism.transaction;

import com.prism.fx.Corridor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "card_transaction")
public class Transaction {

    @Id
    private String id;

    private String employee;
    private String merchant;
    private String merchantCountry;

    @Enumerated(EnumType.STRING)
    private Corridor corridor;

    @Column(precision = 19, scale = 2)
    private BigDecimal purchaseAmount;

    private String fundingCurrency;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private Instant authorizedAt;
    private Instant settledAt;

    @Column(precision = 19, scale = 8)
    private BigDecimal midAuthRate;

    @Column(precision = 19, scale = 8)
    private BigDecimal midSettleRate;

    protected Transaction() {
    }

    public Transaction(String id, String employee, String merchant, String merchantCountry,
                       Corridor corridor, BigDecimal purchaseAmount, String fundingCurrency,
                       TransactionStatus status, Instant authorizedAt, Instant settledAt,
                       BigDecimal midAuthRate, BigDecimal midSettleRate) {
        this.id = id;
        this.employee = employee;
        this.merchant = merchant;
        this.merchantCountry = merchantCountry;
        this.corridor = corridor;
        this.purchaseAmount = purchaseAmount;
        this.fundingCurrency = fundingCurrency;
        this.status = status;
        this.authorizedAt = authorizedAt;
        this.settledAt = settledAt;
        this.midAuthRate = midAuthRate;
        this.midSettleRate = midSettleRate;
    }

    public String getId() {
        return id;
    }

    public String getEmployee() {
        return employee;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getMerchantCountry() {
        return merchantCountry;
    }

    public Corridor getCorridor() {
        return corridor;
    }

    public BigDecimal getPurchaseAmount() {
        return purchaseAmount;
    }

    public String getFundingCurrency() {
        return fundingCurrency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public BigDecimal getMidAuthRate() {
        return midAuthRate;
    }

    public BigDecimal getMidSettleRate() {
        return midSettleRate;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public void setMidSettleRate(BigDecimal midSettleRate) {
        this.midSettleRate = midSettleRate;
    }
}

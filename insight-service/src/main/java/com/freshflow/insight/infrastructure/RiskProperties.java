package com.freshflow.insight.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code freshflow.insight.risk} — FR13's two configured numbers.
 *
 * <p>Both are recorded in spec-01 &sect;2.7 alongside the FR12 keys. FR13 states neither, and
 * neither can be derived from anything the specifications do state, so both are configuration.
 */
@ConfigurationProperties(prefix = "freshflow.insight.risk")
public class RiskProperties {

    /**
     * Units of projected shortfall above which a batch is reported at risk. Zero would flag a
     * batch expected to miss clearing by a single unit, which is noise rather than a signal.
     */
    private double shortfallThreshold = 5.0;

    /**
     * How many of each 24 hours the shop is open and selling. FR13 projects over "remaining
     * trading hours" and no specification defines the trading day; 24 means every remaining hour
     * counts as a selling hour.
     */
    private double tradingHoursPerDay = 24.0;

    public double getShortfallThreshold() {
        return shortfallThreshold;
    }

    public void setShortfallThreshold(double shortfallThreshold) {
        this.shortfallThreshold = shortfallThreshold;
    }

    public double getTradingHoursPerDay() {
        return tradingHoursPerDay;
    }

    public void setTradingHoursPerDay(double tradingHoursPerDay) {
        this.tradingHoursPerDay = tradingHoursPerDay;
    }
}

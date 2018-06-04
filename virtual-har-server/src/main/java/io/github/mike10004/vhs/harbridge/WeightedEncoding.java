package io.github.mike10004.vhs.harbridge;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Iterator;

import static java.util.Objects.requireNonNull;

class WeightedEncoding {

    private static final Splitter QUALITY_VALUE_SPLITTER = Splitter.on(';').trimResults().omitEmptyStrings();

    public final String encoding;
    public final BigDecimal weight;

    public WeightedEncoding(String encoding, BigDecimal weight) {
        this.encoding = requireNonNull(encoding);
        this.weight = requireNonNull(weight);
    }

    /**
     * Parses a string like `gzip;q=1.0` into an instance.
     * @param token the string token
     * @return the weighted encoding instance
     */
    public static WeightedEncoding parse(String token) {
        token = Strings.nullToEmpty(token).trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("string has no usable content");
        }
        Iterator<String> it = QUALITY_VALUE_SPLITTER.split(token).iterator();
        String encoding = it.next();
        @Nullable BigDecimal qValue = null;
        if (it.hasNext()) {
            String qStr = it.next();
            qValue = parseQualityValue(qStr);
        }
        if (qValue == null) {
            qValue = BigDecimal.ONE; // "When not present, the default value is 1." <-- https://developer.mozilla.org/en-US/docs/Glossary/Quality_values
        }
        return new WeightedEncoding(encoding, qValue);
    }

    @Nullable
    static BigDecimal parseQualityValue(@Nullable String qStr) {
        if (qStr != null) {
            String[] parts = qStr.split("\\s*=\\s*", 2);
            if (parts.length == 2) {
                String valueStr = parts[1];
                return valueOf(valueStr);
            }
        }
        return null;
    }

    private static BigDecimal valueOf(String decimal) {
        if ("1.0".equals(decimal) || decimal.matches("^1(\\.0+)?$")) {
            return BigDecimal.ONE;
        }
        if ("0".equals(decimal) || "0.0".equals(decimal) || decimal.matches("^0(\\.0+)?$")) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(decimal);
    }

    public boolean isPositive() {
        return weight.compareTo(BigDecimal.ZERO) > 0;
    }

    public AcceptDecision accepts(String encoding) {
        if (this.encoding.equals(encoding)) {
            return isPositive() ? AcceptDecision.ACCEPT : AcceptDecision.REJECT;
        }
        return AcceptDecision.INDETERMINATE;
    }
}

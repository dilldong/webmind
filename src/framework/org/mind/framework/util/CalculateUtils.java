package org.mind.framework.util;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * BigDecimal tools
 * <br/> add, subtract, multiply, divide
 * <br/> format, formatCurrency, formatNumberSymbol, formatPercent
 *
 * @author dp
 * @version 1.1
 * @date 2021-05-21
 */
public class CalculateUtils {

    public static String format(String number) {
        return format(number, RoundingMode.HALF_UP, 2);
    }

    public static String format(BigDecimal number) {
        return format(number, RoundingMode.HALF_UP, 2);
    }

    public static String format(String number, int scale) {
        return format(number, RoundingMode.HALF_UP, scale);
    }

    public static String format(BigDecimal number, int scale) {
        return format(number, RoundingMode.HALF_UP, scale);
    }

    public static String format(String number, RoundingMode mode, int scale) {
        String afterPart = StringUtils.substringAfter(number, IOUtils.DOT_SEPARATOR);
        if (afterPart.length() > scale)
            return format(new BigDecimal(number), mode, scale);

        return number;
    }

    public static String format(BigDecimal number, RoundingMode mode, int scale) {
        return number.setScale(scale, mode).stripTrailingZeros().toPlainString();
    }

    public static String add(String... augends) {
        if (Objects.isNull(augends) || augends.length == 0)
            return "0";

        BigDecimal total = BigDecimal.ZERO;
        for (String augend : augends)
            total = add(total, new BigDecimal(augend));

        return total.stripTrailingZeros().toPlainString();
    }

    public static BigDecimal add(BigDecimal... augends) {
        if (Objects.isNull(augends) || augends.length == 0)
            return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal augend : augends)
            total = add(total, augend);

        return total;
    }


    public static BigDecimal add(String augend, String augend1) {
        return add(augend, augend1, 8);
    }

    public static BigDecimal add(BigDecimal augend, BigDecimal augend1) {
        return add(augend, augend1, 8);
    }

    public static BigDecimal add(String augend, String augend1, int scale) {
        return add(augend, augend1, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal add(BigDecimal augend, BigDecimal augend1, int scale) {
        return add(augend, augend1, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal add(String augend, String augend1, RoundingMode mode, int scale) {
        return add(new BigDecimal(augend), new BigDecimal(augend1), mode, scale);
    }

    public static BigDecimal add(BigDecimal augend, BigDecimal augend1, RoundingMode mode, int scale) {
        BigDecimal result = augend.add(augend1);
        return result.setScale(scale, mode);
    }

    public static BigDecimal subtract(BigDecimal subtrahend, BigDecimal subtrahend1) {
        return subtract(subtrahend, subtrahend1, 8);
    }

    public static BigDecimal subtract(String subtrahend, String subtrahend1) {
        return subtract(subtrahend, subtrahend1, 8);
    }

    public static BigDecimal subtract(String subtrahend, String subtrahend1, int scale) {
        return subtract(subtrahend, subtrahend1, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal subtract(BigDecimal subtrahend, BigDecimal subtrahend1, int scale) {
        return subtract(subtrahend, subtrahend1, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal subtract(String subtrahend, String subtrahend1, RoundingMode mode, int scale) {
        return subtract(new BigDecimal(subtrahend), new BigDecimal(subtrahend1), mode, scale);
    }

    public static BigDecimal subtract(BigDecimal subtrahend, BigDecimal subtrahend1, RoundingMode mode, int scale) {
        BigDecimal result = subtrahend.subtract(subtrahend1);
        return result.setScale(scale, mode);
    }

    public static String multiply(String... multiplicands) {
        if (Objects.isNull(multiplicands) || multiplicands.length == 0)
            return "0";

        BigDecimal total = BigDecimal.ZERO;
        for (String multiplicand : multiplicands)
            total = multiply(total, new BigDecimal(multiplicand));

        return total.stripTrailingZeros().toPlainString();
    }

    public static BigDecimal multiply(BigDecimal... multiplicands) {
        if (Objects.isNull(multiplicands) || multiplicands.length == 0)
            return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ONE;
        for (BigDecimal multiplicand : multiplicands)
            total = multiply(total, multiplicand);

        return total;
    }

    public static BigDecimal multiply(String multiplicand, String multiplier) {
        return multiply(multiplicand, multiplier, 8);
    }

    public static BigDecimal multiply(BigDecimal multiplicand, BigDecimal multiplier) {
        return multiply(multiplicand, multiplier, 8);
    }

    public static BigDecimal multiply(String multiplicand, String multiplier, int scale) {
        return multiply(multiplicand, multiplier, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal multiply(BigDecimal multiplicand, BigDecimal multiplier, int scale) {
        return multiply(multiplicand, multiplier, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal multiply(String multiplicand, String multiplier, RoundingMode mode, int scale) {
        return multiply(new BigDecimal(multiplicand), new BigDecimal(multiplier), mode, scale);
    }

    public static BigDecimal multiply(BigDecimal multiplicand, BigDecimal multiplier, RoundingMode mode, int scale) {
        if (multiplicand.compareTo(BigDecimal.ZERO) == 0 || multiplier.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;

        return multiplicand.multiply(multiplier).setScale(scale, mode);
    }

    public static BigDecimal divide(String dividend, String divisor) {
        return divide(dividend, divisor, 8);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return divide(dividend, divisor, 8);
    }

    public static BigDecimal divide(String dividend, String divisor, int scale) {
        return divide(dividend, divisor, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, int scale) {
        return divide(dividend, divisor, RoundingMode.HALF_UP, scale);
    }

    public static BigDecimal divide(String dividend, String divisor, RoundingMode mode, int scale) {
        return divide(new BigDecimal(dividend), new BigDecimal(divisor), mode, scale);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, RoundingMode mode, int scale) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;

        return dividend.divide(divisor, scale, mode);
    }

    public static String formatCurrency(String amount) {
        return formatCurrency(amount, Locale.getDefault());
    }

    public static String formatNumberSymbol(String amount) {
        String intpart = StringUtils.substringBefore(amount, IOUtils.DOT_SEPARATOR);
        int size = intpart.length();

        if (size > 3) {
            StringBuilder str = new StringBuilder();
            int newSize = size + (int) Math.ceil(size / 3D - 1);
            char[] newChars = new char[newSize];
            char[] chars = intpart.toCharArray();

            for (int i = size - 1, j = newSize - 1, index = 1; i >= 0; i--) {
                newChars[j--] = chars[i];

                if (index % 3 == 0 && j >= 0)
                    newChars[j--] = ',';
                index++;
            }

            String decimals = StringUtils.substringAfter(amount, IOUtils.DOT_SEPARATOR);
            return StringUtils.isEmpty(decimals) ?
                    str.append(newChars).toString() :
                    str.append(newChars).append(IOUtils.DOT_SEPARATOR).append(decimals).toString();
        }

//        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.getDefault());
//        DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
//        symbols.setGroupingSeparator('\'');
//        formatter.setDecimalFormatSymbols(symbols);
//        return formatter.mobile(new BigDecimal(amount));

        return amount;
    }

    public static String formatCurrency(BigDecimal amount) {
        return formatCurrency(amount, Locale.getDefault());
    }

    public static String formatCurrency(String amount, Locale locale) {
        return formatCurrency(amount, locale, false);
    }

    public static String formatCurrency(BigDecimal amount, Locale locale) {
        return formatCurrency(amount, locale, false);
    }

    public static String formatCurrency(String amount, Locale locale, boolean symbol) {
        return formatCurrency(new BigDecimal(amount), locale, symbol);
    }

    /**
     * Create currency formatting
     *
     * @param amount
     * @param locale
     * @param symbol
     * @return
     */
    public static String formatCurrency(BigDecimal amount, Locale locale, boolean symbol) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(locale);
        String result = currency.format(amount);
        if (symbol)
            return result;

        return StringUtils.startsWith(result, "-") ?
                String.format("-%s", result.substring(2)) :
                result.substring(1);
    }


    public static String formatPercent(String amount) {
        return formatPercent(amount, 2);
    }

    public static String formatPercent(BigDecimal amount) {
        return formatPercent(amount, 2);
    }

    public static String formatPercent(String amount, int scale) {
        return formatPercent(amount, scale, Locale.getDefault());
    }

    public static String formatPercent(BigDecimal amount, int scale) {
        return formatPercent(amount, scale, Locale.getDefault());
    }

    public static String formatPercent(String amount, int scale, boolean symbol) {
        return formatPercent(amount, scale, symbol, Locale.getDefault());
    }

    public static String formatPercent(BigDecimal amount, int scale, boolean symbol) {
        return formatPercent(amount, scale, symbol, Locale.getDefault());
    }

    public static String formatPercent(String amount, int scale, Locale locale) {
        return formatPercent(amount, scale, false, locale);
    }

    public static String formatPercent(BigDecimal amount, int scale, Locale locale) {
        return formatPercent(amount, scale, false, locale);
    }

    public static String formatPercent(String amount, int scale, boolean symbol, Locale locale) {
        return formatPercent(new BigDecimal(amount), scale, symbol, locale);
    }

    /**
     * Create percent-formatted
     *
     * @param amount
     * @param scale
     * @param symbol true: contain the '%', otherwise doesn't contain the '%'
     * @param locale
     * @return
     */
    public static String formatPercent(BigDecimal amount, int scale, boolean symbol, Locale locale) {
        NumberFormat percent = NumberFormat.getPercentInstance(locale);
        percent.setMaximumFractionDigits(scale); //Maximum decimals
        String result = percent.format(amount);
        return symbol ? result : StringUtils.substringBeforeLast(result, "%");
    }

}

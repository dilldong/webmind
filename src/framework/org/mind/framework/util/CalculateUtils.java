package org.mind.framework.util;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * @author Ping
 * @version 1.0
 * @date 2021-05-21
 */
public class CalculateUtils {

    public static String format(String number) {
        return format(number, BigDecimal.ROUND_HALF_UP, 2);
    }

    public static String format(String number, int scale) {
        return format(number, BigDecimal.ROUND_HALF_UP, scale);
    }

    public static String format(String number, int mode, int scale) {
        String afterPart = StringUtils.substringAfter(number, ".");
        if (afterPart.length() > scale)
            return new BigDecimal(number).setScale(scale, mode).stripTrailingZeros().toPlainString();

        return number;
    }

    public static String add(String... augends) {
        final int size = augends.length;
        if (augends == null || size == 0)
            return "0";

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < size; i++) {
            total = add(total, new BigDecimal(augends[i]));
        }

        return total.stripTrailingZeros().toPlainString();
    }


    public static BigDecimal add(String augend, String augend1) {
        return add(augend, augend1, 8);
    }

    public static BigDecimal add(BigDecimal augend, BigDecimal augend1) {
        return add(augend, augend1, 8);
    }

    public static BigDecimal add(String augend, String augend1, int scale) {
        return add(augend, augend1, BigDecimal.ROUND_HALF_UP, scale);
    }

    public static BigDecimal add(BigDecimal augend, BigDecimal augend1, int scale) {
        return add(augend, augend1, BigDecimal.ROUND_HALF_UP, scale);
    }

    public static BigDecimal add(String augend, String augend1, int mode, int scale) {
        return add(new BigDecimal(augend), new BigDecimal(augend1), mode, scale);
    }

    public static BigDecimal add(BigDecimal augend, BigDecimal augend1, int mode, int scale) {
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
        return subtract(subtrahend, subtrahend1, BigDecimal.ROUND_HALF_UP, scale);
    }

    public static BigDecimal subtract(BigDecimal subtrahend, BigDecimal subtrahend1, int scale) {
        return subtract(subtrahend, subtrahend1, BigDecimal.ROUND_HALF_UP, scale);
    }

    public static BigDecimal subtract(String subtrahend, String subtrahend1, int mode, int scale) {
        return subtract(new BigDecimal(subtrahend), new BigDecimal(subtrahend1), mode, scale);
    }

    public static BigDecimal subtract(BigDecimal subtrahend, BigDecimal subtrahend1, int mode, int scale) {
        BigDecimal result = subtrahend.subtract(subtrahend1);
        return result.setScale(scale, mode);
    }

    public static String multiply(String... augends) {
        final int size = augends.length;
        if (augends == null || size == 0)
            return "0";

        BigDecimal total = BigDecimal.ONE;
        for (int i = 0; i < size; i++) {
            total = multiply(total.toPlainString(), augends[i]);
        }

        return total.stripTrailingZeros().toPlainString();
    }

    public static BigDecimal multiply(String multiplicand, String multiplier) {
        return multiply(multiplicand, multiplier, 8);
    }

    public static BigDecimal multiply(String multiplicand, String multiplier, int scale) {
        return multiply(multiplicand, multiplier, BigDecimal.ROUND_HALF_UP, scale);
    }

    public static BigDecimal multiply(String multiplicand, String multiplier, int mode, int scale) {
        BigDecimal multidDecimal = new BigDecimal(multiplicand);
        BigDecimal multirDecimal = new BigDecimal(multiplier);
        if (multidDecimal.compareTo(BigDecimal.ZERO) == 0 || multirDecimal.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;

        return multidDecimal.multiply(multirDecimal).setScale(scale, mode);
    }

    public static BigDecimal divide(String dividend, String divisor) {
        return divide(dividend, divisor, 8);
    }

    public static BigDecimal divide(String dividend, String divisor, int scale) {
        return divide(dividend, divisor, BigDecimal.ROUND_HALF_UP, scale);
    }

    public static BigDecimal divide(String dividend, String divisor, int mode, int scale) {
        BigDecimal dividendDecimal = new BigDecimal(dividend);
        BigDecimal divisorDecimal = new BigDecimal(divisor);
        if (divisorDecimal.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;

        BigDecimal result = dividendDecimal.divide(divisorDecimal, scale, mode);
        return result;
    }

    public static String formatCurrency(String amount) {
        return formatCurrency(amount, Locale.getDefault());
    }

    public static String formatNumberSymbol(String amount) {
        String intpart = StringUtils.substringBefore(amount, ".");
        int size = intpart.length();

        if (size > 3) {
            StringBuffer str = new StringBuffer();
            int newSize = size + (int) Math.ceil(size / 3D - 1);
            char[] newChars = new char[newSize];
            char[] chars = intpart.toCharArray();

            for (int i = size - 1, j = newSize - 1, index = 1; i >= 0; i--) {
                newChars[j--] = chars[i];

                if (index % 3 == 0 && j >= 0)
                    newChars[j--] = ',';
                index++;
            }

            return str.append(newChars).append(".").append(StringUtils.substringAfter(amount, ".")).toString();
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
        // 建立货币格式化引用
        return formatCurrency(new BigDecimal(amount), locale, symbol);
    }

    public static String formatCurrency(BigDecimal amount, Locale locale, boolean symbol) {
        // 建立货币格式化引用
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

    public static String formatPercent(String amount, int scale, Locale locale) {
        return formatPercent(amount, scale, locale, false);
    }

    public static String formatPercent(BigDecimal amount, int scale, Locale locale) {
        return formatPercent(amount, scale, locale, false);
    }

    public static String formatPercent(String amount, int scale, Locale locale, boolean symbol) {
        return formatPercent(new BigDecimal(amount), scale, locale, symbol);
    }

    public static String formatPercent(BigDecimal amount, int scale, Locale locale, boolean symbol) {
        // 建立百分比格式化引用
        NumberFormat percent = NumberFormat.getPercentInstance(locale);
        percent.setMaximumFractionDigits(scale); //百分比小数点最多位数
        String result = percent.format(amount);
        return symbol ? result : StringUtils.substringBeforeLast(result, "%");
    }

}

package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.raoulvdberge.refinedstorage.api.util.IQuantityFormatter;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraftforge.fluids.Fluid;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class QuantityFormatter implements IQuantityFormatter {
    private final DecimalFormat formatterWithUnits = new DecimalFormat("####0.#", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat formatter = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat bucketFormatter = new DecimalFormat("####0.###", DecimalFormatSymbols.getInstance(Locale.US));

    public QuantityFormatter() {
        formatterWithUnits.setRoundingMode(RoundingMode.DOWN);
    }

    @Override
    public String formatWithUnits(long qty) {
        if (qty >= 1_000_000_000_000L) {
            return formatterWithUnits.format((double) qty / 1_000_000_000_000L) + "T";
        } else if (qty >= 1_000_000_000) {
            return formatterWithUnits.format((double) qty / 1_000_000_000) + "B";
        } else if (qty >= 1_000_000) {
            double qtyShort = (double) qty / 1_000_000D;

            if (qty >= 100_000_000) {
                qtyShort = Math.floor(qtyShort); // XXX.XM looks weird.
            }

            return formatterWithUnits.format(qtyShort) + "M";
        } else if (qty >= 1000) {
            double qtyShort = (double) qty / 1000F;

            if (qty >= 100_000) {
                qtyShort = Math.floor(qtyShort); // XXX.XK looks weird.
            }

            return formatterWithUnits.format(qtyShort) + "K";
        }

        return String.valueOf(qty);
    }

    @Override
    public String format(long qty) {
        return formatter.format(qty);
    }

    @Override
    public String formatInBucketForm(long qty) {
        return bucketFormatter.format((double) qty / (double) Fluid.BUCKET_VOLUME) + " B";
    }

    @Override
    public String formatInBucketFormWithOnlyTrailingDigitsIfZero(long qty) {
        double amountRaw = ((double) qty / (double) Fluid.BUCKET_VOLUME);
        long amount = (long) amountRaw;

        if (amount >= 1) {
            return API.instance().getQuantityFormatter().formatWithUnits(amount);
        } else {
            return String.format("%.1f", amountRaw);
        }
    }
}

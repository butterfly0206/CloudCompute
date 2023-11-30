package com.autodesk.compute;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class RFC3339DateFormat extends DateFormat {

    static DateFormat df = initDateFormat();

    private static DateFormat initDateFormat() {
        final SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        result.setTimeZone(TimeZone.getTimeZone("GMT"));
        return result;
    }

    // Same as ISO8601DateFormat but serializing milliseconds.
    @Override
    public StringBuffer format(final Date date, final StringBuffer toAppendTo, final FieldPosition fieldPosition) {
        return toAppendTo.append(df.format(date));
    }

    @Override
    public Date parse(final String source, final ParsePosition pos) {
        return df.parse(source, pos);
    }

}
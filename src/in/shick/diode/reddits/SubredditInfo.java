/*
 * Copyright 2013 Michael Shick
 *
 * This file is part of "diode".
 *
 * "diode" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "diode" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "diode".  If not, see <http://www.gnu.org/licenses/>.
 */

package in.shick.diode.reddits;

import android.content.Context;

import java.io.Serializable;
import java.net.URL;
import java.util.Date;

import in.shick.diode.R;

public class SubredditInfo implements Serializable, Comparable<SubredditInfo>
{
    public static final long SEC_PER_MIN = 60L, SEC_PER_HOUR = SEC_PER_MIN * 60L,
                             SEC_PER_DAY = SEC_PER_HOUR * 24L, SEC_PER_MON = SEC_PER_DAY * 30L,
                             SEC_PER_YR = SEC_PER_DAY * 365L;
    public String name;
    public String description;
    public boolean nsfw;
    public int subscribers;
    public URL url;
    public Date created;

    public String getAgeString(Context context)
    {
        long timeDelta = (System.currentTimeMillis() - created.getTime()) / 1000;
        String output = "";
        String format = context.getString(R.string.second_age_format);
        long divisor = 1;
        if(timeDelta >= SEC_PER_YR)
        {
            format = context.getString(R.string.year_age_format);
            divisor = SEC_PER_YR;
        }
        else if(timeDelta > SEC_PER_MON)
        {
            format = context.getString(R.string.month_age_format);
            divisor = SEC_PER_MON;
        }
        else if(timeDelta > SEC_PER_DAY)
        {
            format = context.getString(R.string.day_age_format);
            divisor = SEC_PER_DAY;
        }
        else if(timeDelta > SEC_PER_HOUR)
        {
            format = context.getString(R.string.hour_age_format);
            divisor = SEC_PER_HOUR;
        }
        else if(timeDelta > SEC_PER_MIN)
        {
            format = context.getString(R.string.minute_age_format);
            divisor = SEC_PER_MIN;
        }

        output += String.format(format, timeDelta / divisor);
        if(timeDelta >= divisor * 2)
        {
            output += "s";
        }
        return output;
    }

    @Override
    public int compareTo(SubredditInfo other)
    {
        return name.compareToIgnoreCase(other.name);
    }

    @Override
    public int hashCode()
    {
        return name.toLowerCase().hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        return hashCode() == other.hashCode();
    }
}

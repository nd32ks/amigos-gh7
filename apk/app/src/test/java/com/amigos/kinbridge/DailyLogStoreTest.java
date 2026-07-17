package com.amigos.kinbridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

/** Per-day activity log: date-derived filenames roll over at midnight. */
public class DailyLogStoreTest {

    private Date date(int year, int month, int day, int hour) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, hour, 0, 0);
        return cal.getTime();
    }

    @Test
    public void fileName_usesCalendarDate() {
        assertEquals("activity_2026-07-17.txt",
                DailyLogStore.fileNameFor(date(2026, Calendar.JULY, 17, 9)));
    }

    @Test
    public void fileName_rollsOverAtMidnight() {
        String before = DailyLogStore.fileNameFor(date(2026, Calendar.JULY, 17, 23));
        String after = DailyLogStore.fileNameFor(date(2026, Calendar.JULY, 18, 0));
        assertNotEquals(before, after);
    }

    @Test
    public void formatLine_prefixesTimeAndSpeaker() {
        assertEquals("[09:14] Ibu Sri: Pagi, Kenang.",
                DailyLogStore.formatLine("09:14", "Ibu Sri", "Pagi, Kenang."));
        assertEquals("[21:40] Kenang: Selamat malam, Ibu.",
                DailyLogStore.formatLine("21:40", "Kenang", "Selamat malam, Ibu."));
    }

    @Test
    public void formatDiarySection_stampsDateAndTime() {
        assertEquals("\n--- Buku harian (2026-07-17 21:40) ---\nIbu menonton Game of Thrones.",
                DailyLogStore.formatDiarySection("2026-07-17 21:40", "Ibu menonton Game of Thrones."));
    }
}

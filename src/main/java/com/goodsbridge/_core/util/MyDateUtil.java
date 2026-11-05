package com.goodsbridge._core.util;

import java.sql.Date;
import java.sql.Timestamp;
import org.apache.commons.lang3.time.DateFormatUtils;

public class MyDateUtil {
    // Timestamp를 원하는 포맷의 문자열로 변환
    public static String timestampFormat(Timestamp timestamp) {
        Date currentDate = new Date(timestamp.getTime());
        return DateFormatUtils.format(currentDate, "yyyy-MM-dd HH:mm");
    }
}

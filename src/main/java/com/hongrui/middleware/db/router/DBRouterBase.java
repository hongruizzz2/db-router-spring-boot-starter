package com.hongrui.middleware.db.router;

/**
 * @author hongrui
 * @description
 * @date 2024-11-17 13:47
 */
public class DBRouterBase {

    private String tbIdx;

    public String getTbIdx() {
        return DBContextHolder.getTBKey();
    }

}

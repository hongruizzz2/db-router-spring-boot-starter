package com.hongrui;

import com.hongrui.middleware.db.router.annotation.DBRouter;

/**
 * @author hongrui
 * @description
 * @date 2024-11-17 15:50
 */
public interface IUserDao {

    @DBRouter(key = "userId")
    void insertUser(String req);

}

package com.hongrui.middleware.db.router.util;

/**
 * @author hongrui
 * @description
 * @date 2024-11-17 15:44
 */
public class StringUtils {

    public static String middleScoreToCamelCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = false;
        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);

            if (currentChar == '-') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    result.append(Character.toUpperCase(currentChar));
                    nextUpperCase = false;
                } else {
                    result.append(currentChar);
                }
            }
        }
        return result.toString();
    }

}

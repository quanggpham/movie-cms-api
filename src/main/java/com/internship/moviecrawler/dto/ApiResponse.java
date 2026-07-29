package com.internship.moviecrawler.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Standard API response envelope.
 *
 * <ul>
 *   <li>Success: {@code data} is populated, {@code error} is null</li>
 *   <li>Error:   {@code error} is populated, {@code data} is null</li>
 * </ul>
 *
 * @param <T>     type of the data payload
 * @param success whether the request succeeded
 * @param status  HTTP status code (mirrored in body for proxy resilience)
 * @param data    response payload (null on error)
 * @param error   error detail (null on success)
 */
public record ApiResponse<T>(boolean success, int status, T data, ErrorDetail error) {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Serialize this response to pretty-printed JSON.
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    // ---- Static factories ----

    public static <T> ApiResponse<T> success(int status, T data) {
        return new ApiResponse<>(true, status, data, null);
    }

    public static <T> ApiResponse<T> error(int status, String code, String message) {
        return new ApiResponse<>(false, status, null, new ErrorDetail(code, message));
    }
}

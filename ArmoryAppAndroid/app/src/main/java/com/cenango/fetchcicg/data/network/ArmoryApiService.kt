package com.cenango.fetchcicg.data.network

import com.cenango.fetchcicg.data.model.Asset
import com.cenango.fetchcicg.data.model.Category
import com.cenango.fetchcicg.data.model.TagFormat
import com.cenango.fetchcicg.data.model.User
import com.cenango.fetchcicg.data.model.requests.AssetMoveRequest
import com.cenango.fetchcicg.data.model.requests.AssetRequest
import com.cenango.fetchcicg.data.model.requests.LoginRequest
import com.cenango.fetchcicg.data.model.requests.RefreshTokenRequest
import com.cenango.fetchcicg.data.model.responses.ApiDetailsResponse
import com.cenango.fetchcicg.data.model.responses.AssetSearchResponse
import com.cenango.fetchcicg.data.model.responses.DashboardResponse
import com.cenango.fetchcicg.data.model.responses.InventoryListResponse
import com.cenango.fetchcicg.data.model.responses.InventorySummaryResponse
import com.cenango.fetchcicg.data.model.responses.LoginResponse
import com.cenango.fetchcicg.data.model.responses.RefreshTokenResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Kotlin/Retrofit equivalent of iOS's `FetchCICGAPI` (API+Blueprint.swift).
 * One function per case; paths, methods and query/body shapes match 1:1.
 */
interface ArmoryApiService {

    // ---- Misc -------------------------------------------------------------

    /** Config-discovery call — resolves which real API host/version to use.
     *  Hits a fixed host (see [ApiSettings.API_DETAILS_HOST]), not the main base URL,
     *  so the full URL is passed in directly. */
    @Headers("${AuthInterceptor.NO_AUTH_HEADER}: true")
    @GET
    suspend fun getApiDetails(@Url url: String): ApiDetailsResponse

    @GET("api/settings/tag/meta")
    suspend fun getTagFormatData(): TagFormat

    @GET("api/categories")
    suspend fun getCategories(): List<Category>

    // ---- Dashboard ----------------------------------------------------------

    @GET("api/dashboard")
    suspend fun getDashboardData(): DashboardResponse

    // ---- Auth ---------------------------------------------------------------

    @Headers("${AuthInterceptor.NO_AUTH_HEADER}: true")
    @POST("api/auth/signin")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @Headers("${AuthInterceptor.NO_AUTH_HEADER}: true")
    @POST("api/auth/token")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): RefreshTokenResponse

    // ---- Assets ---------------------------------------------------------------

    @GET("api/assets/by/epc/{tag}")
    suspend fun getAsset(@Path("tag") tag: String): Asset

    @POST("api/assets")
    suspend fun enrollAsset(@Body body: AssetRequest): Asset

    @POST("api/assets/{assetId}/return")
    suspend fun checkinAsset(@Path("assetId") assetId: Int, @Body body: AssetMoveRequest): Asset

    @POST("api/assets/{assetId}/approve-and-return")
    suspend fun checkinApproveAsset(@Path("assetId") assetId: Int, @Body body: AssetMoveRequest): Asset

    /**
     * Same endpoint as [checkinApproveAsset] — used when the *logged-in*
     * user's own approve-and-return attempt came back 403, and an admin
     * authenticates via [CheckinAuthActivity] instead. [token] overrides the
     * `Authorization` header for this call only (see [AuthInterceptor] — it
     * leaves a request alone if one is already set) and is never written to
     * [com.cenango.fetchcicg.data.session.SessionManager]; the admin's
     * session isn't replacing the current user's. Mirrors iOS's
     * `checkinApproveAssetWithSuperUser`, which — unlike the non-superuser
     * version — has no 401/token-refresh retry logic; neither does this.
     */
    @POST("api/assets/{assetId}/approve-and-return")
    suspend fun checkinApproveAssetWithSuperUser(
        @Path("assetId") assetId: Int,
        @Body body: AssetMoveRequest,
        @Header("Authorization") token: String
    ): Asset

    @POST("api/assets/{assetId}/checkout")
    suspend fun checkoutAsset(@Path("assetId") assetId: Int, @Body body: AssetMoveRequest): Asset

    /** [categoryIds] must be pre-formatted as `"[1, 2, 3]"` (i.e. `list.toString()`
     *  in Kotlin) — matches the backend contract inherited from iOS, which sends
     *  Swift's `Array<Int>.description`. */
    @GET("api/assets/search")
    suspend fun searchAssets(
        @Query("q") searchText: String,
        @Query("category_id") categoryIds: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): AssetSearchResponse

    // ---- Inventory --------------------------------------------------------

    @GET("api/inventory")
    suspend fun getInventorySummary(@Query("rfid_tags") tags: String): InventorySummaryResponse

    @GET("api/inventory/list")
    suspend fun getInventoryList(
        @Query("rfid_tags") tags: String,
        @Query("type") group: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): InventoryListResponse

    // ---- Users --------------------------------------------------------------

    @GET("api/users")
    suspend fun getUsers(): List<User>
}

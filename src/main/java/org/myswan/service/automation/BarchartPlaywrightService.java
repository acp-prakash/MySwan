package org.myswan.service.automation;

import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.myswan.model.AppCache;
import org.myswan.service.internal.AppCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class BarchartPlaywrightService {

    @Value("${barchart.login.url}")
    private String loginUrl;

    @Value("${barchart.login.username}")
    private String username;

    @Value("${barchart.login.password}")
    private String password;

    @Value("${barchart.login.target.api}")
    private String targetApiUrl;

    private final AppCacheService appCacheService;

    public BarchartPlaywrightService(AppCacheService appCacheService) {
        this.appCacheService = appCacheService;
    }

    /**
     * Performs auto-login to Barchart and extracts cookies and x-xsrf-token
     * @return Map containing status, cookie, and token
     */
    public Map<String, String> autoLoginAndExtractCredentials() {
        Map<String, String> result = new HashMap<>();

        try (Playwright playwright = Playwright.create()) {
            log.info("Starting Playwright browser automation for Barchart login...");

            // Launch browser in headless mode
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // Set to true for production, false for debugging
                    .setTimeout(60000));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

            Page page = context.newPage();

            // Storage for extracted credentials
            AtomicReference<String> extractedToken = new AtomicReference<>();
            AtomicReference<String> extractedCookie = new AtomicReference<>();
            CompletableFuture<Boolean> tokenExtracted = new CompletableFuture<>();

            // Set up request/response interceptor to capture x-xsrf-token and cookies
            page.onRequest(request -> {
                String url = request.url();
                // Specifically target the API call: https://www.barchart.com/proxies/core-api/v1/quotes/get?meta=
                if (url.contains("/proxies/core-api/v1/quotes/get") && url.contains("meta=")) {
                    log.info("✅ Intercepted TARGET API request: {}", url);
                    Map<String, String> headers = request.headers();

                    // Extract x-xsrf-token from request headers
                    if (headers.containsKey("x-xsrf-token")) {
                        String token = headers.get("x-xsrf-token");
                        // Decode URL-encoded token (e.g., %3D -> =)
                        try {
                            token = URLDecoder.decode(token, StandardCharsets.UTF_8);
                            log.info("✅ Found x-xsrf-token in request headers (decoded): {}", token.substring(0, Math.min(20, token.length())) + "...");
                        } catch (Exception e) {
                            log.warn("Failed to decode token, using as-is: {}", e.getMessage());
                        }
                        extractedToken.set(token);
                    }

                    // Extract cookies from request headers
                    if (headers.containsKey("cookie")) {
                        String cookie = headers.get("cookie");
                        log.info("✅ Found cookie in request headers (length: {})", cookie.length());
                        extractedCookie.set(cookie);
                    }

                    // Mark as complete if both are found
                    if (extractedToken.get() != null && extractedCookie.get() != null) {
                        log.info("✅ Successfully extracted both token and cookie from target API!");
                        tokenExtracted.complete(true);
                    }
                } else if (url.contains("core-api") || url.contains("quotes")) {
                    // Log other API calls for debugging
                    log.debug("📋 Other API call detected: {}", url);
                }
            });

            page.onResponse(response -> {
                String url = response.url();
                // Specifically target the API call: https://www.barchart.com/proxies/core-api/v1/quotes/get?meta=
                if (url.contains("/proxies/core-api/v1/quotes/get") && url.contains("meta=")) {
                    log.info("✅ Intercepted TARGET API response: {}", url);
                    Map<String, String> headers = response.headers();

                    // Sometimes token is in response headers (as fallback)
                    if (headers.containsKey("x-xsrf-token") && extractedToken.get() == null) {
                        String token = headers.get("x-xsrf-token");
                        // Decode URL-encoded token (e.g., %3D -> =)
                        try {
                            token = URLDecoder.decode(token, StandardCharsets.UTF_8);
                            log.info("✅ Found x-xsrf-token in response headers (decoded): {}", token.substring(0, Math.min(20, token.length())) + "...");
                        } catch (Exception e) {
                            log.warn("Failed to decode token from response, using as-is: {}", e.getMessage());
                        }
                        extractedToken.set(token);
                    }
                } else if (url.contains("core-api") || url.contains("quotes")) {
                    log.debug("📋 Other API response: {}", url);
                }
            });

            // Navigate to login page
            log.info("Navigating to login page: {}", loginUrl);
            page.navigate(loginUrl, new Page.NavigateOptions().setTimeout(30000));
            page.waitForLoadState();

            // Wait for login form
            log.info("Waiting for login form...");
            page.waitForSelector("input[type='email'], input[name='email'], input[id='email'], input[placeholder*='email']",
                    new Page.WaitForSelectorOptions().setTimeout(10000));

            // Fill in username/email
            log.info("Entering username/email...");
            Locator emailInput = page.locator("input[type='email'], input[name='email'], input[id='email'], input[placeholder*='email']").first();
            emailInput.fill(username);

            // Fill in password
            log.info("Entering password...");
            Locator passwordInput = page.locator("input[type='password'], input[name='password'], input[id='password']").first();
            passwordInput.fill(password);

            // Click login button
            log.info("Clicking login button...");
            Locator loginButton = page.locator("button[type='submit'], button:has-text('Sign In'), button:has-text('Log In'), input[type='submit']").first();
            loginButton.click();

            // Wait for navigation after login
            log.info("Waiting for successful login...");
            page.waitForLoadState();

            // Navigate to a page that triggers the API call (e.g., stocks page or quotes page)
            log.info("Navigating to trigger API calls...");
            page.navigate("https://www.barchart.com/my/watchlist?viewName=157005", new Page.NavigateOptions().setTimeout(30000));
            page.waitForLoadState();

            // Wait for the token to be extracted (or timeout after 20 seconds)
            try {
                tokenExtracted.get(20, TimeUnit.SECONDS);
                log.info("Successfully extracted credentials!");
            } catch (Exception e) {
                log.warn("Timeout waiting for API call, attempting to extract from cookies...");

                // Fallback: Get cookies directly from browser context
                var cookies = context.cookies();
                StringBuilder cookieBuilder = new StringBuilder();
                for (var cookie : cookies) {
                    if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
                    cookieBuilder.append(cookie.name).append("=").append(cookie.value);

                    // Check for XSRF token in cookies
                    if (cookie.name.equalsIgnoreCase("XSRF-TOKEN") ||
                        cookie.name.equalsIgnoreCase("xsrf-token") ||
                        cookie.name.contains("xsrf")) {
                        String tokenValue = cookie.value;
                        // Decode URL-encoded token
                        try {
                            tokenValue = URLDecoder.decode(tokenValue, StandardCharsets.UTF_8);
                        } catch (Exception ex) {
                            log.warn("Failed to decode cookie token, using as-is");
                        }
                        extractedToken.set(tokenValue);
                        log.info("Found XSRF token in cookie: {}", cookie.name);
                    }
                }

                if (cookieBuilder.length() > 0) {
                    extractedCookie.set(cookieBuilder.toString());
                    log.info("Extracted cookies from browser context (length: {})", cookieBuilder.length());
                }
            }

            // Store the extracted credentials
            String token = extractedToken.get();
            String cookie = extractedCookie.get();

            if (token != null && cookie != null) {
                log.info("Saving credentials to AppCache...");
                AppCache appCache = new AppCache();
                appCache.setBarchartToken(token);
                appCache.setBarchartCookie(cookie);
                appCacheService.update(appCache);

                result.put("status", "success");
                result.put("token", token);
                result.put("cookie", cookie.substring(0, Math.min(100, cookie.length())) + "...");
                result.put("message", "Successfully extracted and saved Barchart credentials");
                log.info("Credentials saved successfully!");
            } else {
                result.put("status", "partial");
                result.put("token", token != null ? token : "NOT_FOUND");
                result.put("cookie", cookie != null ? "FOUND" : "NOT_FOUND");
                result.put("message", "Could not extract all credentials. Token: " + (token != null) + ", Cookie: " + (cookie != null));
                log.warn("Could not extract all credentials");
            }

            // Close browser
            browser.close();

        } catch (Exception e) {
            log.error("Error during Playwright automation: ", e);
            result.put("status", "error");
            result.put("message", "Error: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }

        return result;
    }

    /**
     * Test method to verify current credentials work
     */
    public boolean testCredentials() {
        AppCache cache = appCacheService.getAppCache();
        if (cache == null || cache.getBarchartToken() == null || cache.getBarchartCookie() == null) {
            log.warn("No credentials found in AppCache");
            return false;
        }

        log.info("Credentials exist in AppCache - Token length: {}, Cookie length: {}",
                cache.getBarchartToken().length(),
                cache.getBarchartCookie().length());
        return true;
    }
}


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
public class TradingViewPlaywrightService {

    @Value("${tradingview.login.url}")
    private String loginUrl;

    @Value("${tradingview.login.username}")
    private String username;

    @Value("${tradingview.login.password}")
    private String password;

    @Value("${tradingview.login.target.page}")
    private String targetPage;

    @Value("${tradingview.login.target.api}")
    private String targetApiUrl;

    private final AppCacheService appCacheService;

    public TradingViewPlaywrightService(AppCacheService appCacheService) {
        this.appCacheService = appCacheService;
    }

    /**
     * Performs auto-login to TradingView and extracts cookie only
     * @return Map containing status and cookie
     */
    public Map<String, String> autoLoginAndExtractCredentials() {
        Map<String, String> result = new HashMap<>();

        try (Playwright playwright = Playwright.create()) {
            log.info("Starting Playwright browser automation for TradingView login...");

            // Launch browser in headless mode
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // Set to true for production, false for debugging
                    .setTimeout(60000));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

            Page page = context.newPage();

            // Storage for extracted credentials
            AtomicReference<String> extractedCookie = new AtomicReference<>();
            CompletableFuture<Boolean> cookieExtracted = new CompletableFuture<>();

            // Set up request interceptor to capture cookies from target API call
            page.onRequest(request -> {
                String url = request.url();
                // Specifically target the API call: https://scanner.tradingview.com/bond/scan?label-product=details
                if (url.contains("scanner.tradingview.com") && url.contains("/scan") && url.contains("label-product=details")) {
                    log.info("✅ Intercepted TARGET API request: {}", url);
                    Map<String, String> headers = request.headers();

                    // Extract cookies from request headers
                    if (headers.containsKey("cookie")) {
                        String cookie = headers.get("cookie");
                        // Decode URL-encoded cookie
                        try {
                            cookie = URLDecoder.decode(cookie, StandardCharsets.UTF_8);
                            log.info("✅ Found cookie in request headers (decoded, length: {})", cookie.length());
                        } catch (Exception e) {
                            log.warn("Failed to decode cookie, using as-is: {}", e.getMessage());
                        }
                        extractedCookie.set(cookie);
                        cookieExtracted.complete(true);
                    }
                } else if (url.contains("scanner.tradingview.com")) {
                    // Log other scanner API calls for debugging
                    log.debug("📋 Other TradingView scanner API call: {}", url);
                }
            });

            page.onResponse(response -> {
                String url = response.url();
                // Also check response for the target API
                if (url.contains("scanner.tradingview.com") && url.contains("/scan") && url.contains("label-product=details")) {
                    log.info("✅ Intercepted TARGET API response: {}", url);
                }
            });

            // Navigate to login page
            log.info("Navigating to TradingView login page: {}", loginUrl);
            page.navigate(loginUrl, new Page.NavigateOptions().setTimeout(30000));
            page.waitForLoadState();

            // Wait for login options to appear
            log.info("Waiting for login options...");
            try {
                Thread.sleep(2000); // Wait for page to fully load
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // TradingView has multiple login options (Google, Apple, Facebook, Email)
            // We need to click the "Email" button first
            log.info("Looking for Email login button...");
            try {
                // Try multiple selectors for the Email button
                Locator emailButton = page.locator("button[name='Email'], button:has-text('Email'), button[aria-label='Email']").first();
                emailButton.click();
                log.info("✅ Clicked Email button");
                Thread.sleep(1000); // Wait for email form to appear
            } catch (Exception e) {
                log.warn("Email button not found with primary selectors, trying alternatives...");
                try {
                    // Alternative: look for any button containing "Email"
                    page.click("text=Email");
                    log.info("✅ Clicked Email button (alternative method)");
                    Thread.sleep(1000);
                } catch (Exception ex) {
                    log.error("Failed to find Email button: {}", ex.getMessage());
                    result.put("status", "error");
                    result.put("message", "Failed to find Email login button. TradingView page structure may have changed.");
                    browser.close();
                    return result;
                }
            }

            // Now wait for email/username input field
            log.info("Waiting for email input field...");
            try {
                page.waitForSelector("input[name='username'], input[name='id_username'], input[type='text'], input[autocomplete='username']",
                        new Page.WaitForSelectorOptions().setTimeout(10000));
            } catch (Exception e) {
                log.warn("Email input not found with standard selectors");
            }

            // Fill in username/email
            log.info("Entering username/email...");
            try {
                Locator usernameInput = page.locator("input[name='username'], input[name='id_username'], input[autocomplete='username'], input[type='text']").first();
                usernameInput.fill(username);
                log.info("✅ Username entered");
            } catch (Exception e) {
                log.error("Failed to fill username: {}", e.getMessage());
                result.put("status", "error");
                result.put("message", "Failed to find username field: " + e.getMessage());
                browser.close();
                return result;
            }

            // Fill in password
            log.info("Entering password...");
            try {
                Locator passwordInput = page.locator("input[type='password'], input[name='password'], input[name='id_password']").first();
                passwordInput.fill(password);
                log.info("✅ Password entered");
            } catch (Exception e) {
                log.error("Failed to fill password: {}", e.getMessage());
                result.put("status", "error");
                result.put("message", "Failed to find password field: " + e.getMessage());
                browser.close();
                return result;
            }

            // Click login/sign in button
            log.info("Clicking sign in button...");
            try {
                Locator loginButton = page.locator("button[type='submit'], button:has-text('Sign in'), button:has-text('Log in'), button[name='submit']").first();
                loginButton.click();
                log.info("✅ Sign in button clicked");
            } catch (Exception e) {
                log.error("Failed to click login button: {}", e.getMessage());
                result.put("status", "error");
                result.put("message", "Failed to find login button: " + e.getMessage());
                browser.close();
                return result;
            }

            // Wait for navigation after login
            log.info("Waiting for successful login...");
            try {
                page.waitForLoadState();
                // Wait a bit for any redirects or CAPTCHA
                Thread.sleep(3000);
            } catch (Exception e) {
                log.warn("Post-login wait interrupted: {}", e.getMessage());
            }

            // Check for CAPTCHA / "I'm not a robot" checkbox
            log.info("🔍 Scanning page for CAPTCHA/robot verification...");
            boolean captchaFound = false;

            try {
                // Give CAPTCHA more time to load
                log.info("⏳ Waiting 5 seconds for CAPTCHA to appear...");
                Thread.sleep(5000);

                // Log all iframes on the page for debugging
                log.info("📋 Scanning all iframes on page...");
                var frames = page.frames();
                log.info("📊 Total frames found: {}", frames.size());

                for (int i = 0; i < frames.size(); i++) {
                    Frame frame = frames.get(i);
                    String frameUrl = frame.url();
                    String frameName = frame.name();
                    log.info("  Frame {}: URL={}, Name={}", i, frameUrl, frameName);
                }

                // Strategy 1: Check for reCAPTCHA iframe (most common)
                log.info("🔍 Strategy 1: Looking for reCAPTCHA iframe...");
                for (Frame frame : frames) {
                    String frameUrl = frame.url().toLowerCase();
                    if (frameUrl.contains("recaptcha") || frameUrl.contains("google.com/recaptcha")) {
                        log.info("✅ Found reCAPTCHA iframe: {}", frameUrl);
                        captchaFound = true;

                        try {
                            // Wait for checkbox to be visible in iframe
                            log.info("⏳ Waiting for reCAPTCHA checkbox to be visible...");

                            // Try to wait for rc-inline-block specifically first
                            try {
                                frame.waitForSelector(".rc-inline-block", new Frame.WaitForSelectorOptions().setTimeout(10000));
                                log.info("✅ Found .rc-inline-block element in iframe");
                            } catch (Exception e) {
                                // Fallback to other selectors
                                frame.waitForSelector(
                                    ".recaptcha-checkbox-border, #recaptcha-anchor, .recaptcha-checkbox",
                                    new Frame.WaitForSelectorOptions().setTimeout(10000)
                                );
                            }

                            // Try multiple selectors with rc-inline-block as priority
                            log.info("🖱️ Attempting to click reCAPTCHA checkbox...");
                            boolean clicked = false;

                            // PRIORITY 1: rc-inline-block (the specific class you identified)
                            try {
                                frame.click(".rc-inline-block");
                                log.info("✅ Clicked reCAPTCHA checkbox using .rc-inline-block");
                                clicked = true;
                            } catch (Exception e1) {
                                log.warn("Could not click .rc-inline-block, trying alternatives...");

                                // PRIORITY 2: recaptcha-checkbox-border
                                try {
                                    frame.click(".recaptcha-checkbox-border");
                                    log.info("✅ Clicked reCAPTCHA checkbox using .recaptcha-checkbox-border");
                                    clicked = true;
                                } catch (Exception e2) {

                                    // PRIORITY 3: recaptcha-anchor
                                    try {
                                        frame.click("#recaptcha-anchor");
                                        log.info("✅ Clicked reCAPTCHA checkbox using #recaptcha-anchor");
                                        clicked = true;
                                    } catch (Exception e3) {

                                        // PRIORITY 4: generic recaptcha-checkbox
                                        try {
                                            frame.click(".recaptcha-checkbox");
                                            log.info("✅ Clicked reCAPTCHA checkbox using .recaptcha-checkbox");
                                            clicked = true;
                                        } catch (Exception e4) {
                                            log.error("❌ All click attempts failed");
                                        }
                                    }
                                }
                            }

                            if (clicked) {
                                log.info("⏳ Waiting 8 seconds for reCAPTCHA verification...");
                                Thread.sleep(8000);
                            }
                            break;

                        } catch (Exception frameEx) {
                            log.error("❌ Failed to interact with reCAPTCHA: {}", frameEx.getMessage());
                        }
                    }
                }

                // Strategy 2: Check for hCaptcha
                if (!captchaFound) {
                    log.info("🔍 Strategy 2: Looking for hCaptcha...");
                    try {
                        int hcaptchaCount = page.locator("iframe[src*='hcaptcha']").count();
                        log.info("📊 hCaptcha iframes found: {}", hcaptchaCount);

                        if (hcaptchaCount > 0) {
                            log.info("✅ Found hCaptcha");
                            captchaFound = true;

                            var hcaptchaFrame = page.frameLocator("iframe[src*='hcaptcha']").first();
                            hcaptchaFrame.locator("#checkbox, .hcaptcha-checkbox, [role='checkbox']").click();
                            log.info("✅ Clicked hCaptcha checkbox");

                            log.info("⏳ Waiting 8 seconds for hCaptcha verification...");
                            Thread.sleep(8000);
                        }
                    } catch (Exception hcaptchaEx) {
                        log.warn("❌ Error checking hCaptcha: {}", hcaptchaEx.getMessage());
                    }
                }

                // Strategy 3: Look for CAPTCHA-related elements on main page
                if (!captchaFound) {
                    log.info("🔍 Strategy 3: Looking for CAPTCHA elements on main page...");
                    try {
                        // Check for various CAPTCHA-related selectors
                        String[] captchaSelectors = {
                            "div[class*='captcha']",
                            "div[id*='captcha']",
                            "div[class*='recaptcha']",
                            "div[id*='recaptcha']",
                            "[data-callback*='captcha']",
                            ".g-recaptcha",
                            "#g-recaptcha"
                        };

                        for (String selector : captchaSelectors) {
                            int count = page.locator(selector).count();
                            if (count > 0) {
                                log.info("📊 Found {} elements matching: {}", count, selector);
                                captchaFound = true;
                            }
                        }
                    } catch (Exception mainPageEx) {
                        log.debug("No CAPTCHA elements found on main page");
                    }
                }

                // Strategy 4: Check for simple checkbox
                if (!captchaFound) {
                    log.info("🔍 Strategy 4: Looking for simple checkbox...");
                    try {
                        String[] checkboxSelectors = {
                            "input[type='checkbox'][id*='captcha']",
                            "input[type='checkbox'][id*='robot']",
                            "input[type='checkbox'][class*='captcha']",
                            "input[type='checkbox'][name*='captcha']"
                        };

                        for (String selector : checkboxSelectors) {
                            if (page.locator(selector).count() > 0) {
                                log.info("✅ Found simple checkbox: {}", selector);
                                page.click(selector);
                                log.info("✅ Clicked simple checkbox");
                                captchaFound = true;
                                Thread.sleep(3000);
                                break;
                            }
                        }
                    } catch (Exception checkboxEx) {
                        log.debug("No simple checkbox found: {}", checkboxEx.getMessage());
                    }
                }

                // Strategy 5: Look for Cloudflare challenge
                if (!captchaFound) {
                    log.info("🔍 Strategy 5: Checking for Cloudflare challenge...");
                    try {
                        if (page.locator("#challenge-form, .cf-turnstile, [id*='cloudflare']").count() > 0) {
                            log.info("⚠️ Cloudflare challenge detected - may require manual intervention");
                            captchaFound = true;
                            Thread.sleep(10000); // Wait longer for Cloudflare
                        }
                    } catch (Exception cfEx) {
                        log.debug("No Cloudflare challenge detected");
                    }
                }

                if (captchaFound) {
                    log.info("🎯 CAPTCHA detected and handled");
                    log.info("⏳ Final wait of 5 seconds to ensure verification completed...");
                    Thread.sleep(5000);
                } else {
                    log.info("✅ No CAPTCHA detected after comprehensive scan, proceeding...");
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("CAPTCHA handling interrupted");
            } catch (Exception captchaException) {
                log.error("❌ Error during CAPTCHA handling: {}", captchaException.getMessage(), captchaException);
                // Continue anyway - CAPTCHA might not be present
            }

            // Navigate to the screener page that triggers the API call
            log.info("Navigating to trigger API calls: {}", targetPage);
            page.navigate(targetPage, new Page.NavigateOptions().setTimeout(30000));
            page.waitForLoadState();

            // Wait a bit for API calls to trigger
            log.info("Waiting for API calls to trigger...");
            Thread.sleep(5000);

            // Wait for the cookie to be extracted (or timeout after 20 seconds)
            try {
                cookieExtracted.get(20, TimeUnit.SECONDS);
                log.info("Successfully extracted cookie!");
            } catch (Exception e) {
                log.warn("Timeout waiting for API call, attempting to extract from browser cookies...");

                // Fallback: Get cookies directly from browser context
                var cookies = context.cookies();
                StringBuilder cookieBuilder = new StringBuilder();
                for (var cookie : cookies) {
                    if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
                    cookieBuilder.append(cookie.name).append("=").append(cookie.value);
                }

                if (cookieBuilder.length() > 0) {
                    String cookieString = cookieBuilder.toString();
                    try {
                        cookieString = URLDecoder.decode(cookieString, StandardCharsets.UTF_8);
                    } catch (Exception ex) {
                        log.warn("Failed to decode cookies from browser context");
                    }
                    extractedCookie.set(cookieString);
                    log.info("Extracted cookies from browser context (length: {})", cookieBuilder.length());
                }
            }

            // Store the extracted credentials
            String cookie = extractedCookie.get();

            if (cookie != null && !cookie.isEmpty()) {
                log.info("Saving TradingView cookie to AppCache...");
                AppCache appCache = new AppCache();
                appCache.setTradingViewCookie(cookie);
                appCacheService.update(appCache);

                result.put("status", "success");
                result.put("cookie", cookie.substring(0, Math.min(100, cookie.length())) + "...");
                result.put("message", "Successfully extracted and saved TradingView cookie");
                log.info("TradingView cookie saved successfully!");
            } else {
                result.put("status", "error");
                result.put("message", "Could not extract cookie");
                log.warn("Could not extract TradingView cookie");
            }

            // Close browser
            browser.close();

        } catch (Exception e) {
            log.error("Error during TradingView Playwright automation: ", e);
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
        if (cache == null || cache.getTradingViewCookie() == null) {
            log.warn("No TradingView cookie found in AppCache");
            return false;
        }

        log.info("TradingView cookie exists in AppCache - Cookie length: {}",
                cache.getTradingViewCookie().length());
        return true;
    }
}


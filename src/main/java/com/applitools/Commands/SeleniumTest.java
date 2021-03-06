package com.applitools.Commands;

import com.applitools.Browser;
import com.applitools.Modes.AttachedSession.AttachedWebDriver;
import com.applitools.Utils.Validator;
import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public abstract class SeleniumTest extends Test {
    private static final String DEFAULT_SERVER = "http://localhost:4444/wd/hub/";
    private static final Browser DEFAULT_BROWSER = Browser.Firefox;

    //Browser
    @Parameter(names = {"-br", "--browser"}, description = "Specify browser from [Chrome|Firefox|Safari|IE] default = Firefox")
    protected String browser;

    //Selenium server
    @Parameter(names = {"-se", "--seleniumServer"}, description = "Specify selenium server url")
    protected String seleniumServerURL;

    //Caps file
    @Parameter(names = {"-cf", "--capsFile"}, description = "Specify capabilities json file")
    protected String capsFile;

    @Parameter(names = {"-iw", "--implicitWait"}, description = "Specify implicit wait in seconds for selenium")
    protected long implicitWait = -1;

    @Parameter(names = {"-lw", "--loadWait"}, description = "Specify page load wait in seconds for selenium")
    protected long pageloadTimeout = -1;

    //Session id to attach
    @Parameter(names = {"-id", "--sessionId"}, description = "Selenium session-id to attach the test")
    protected String sessionId;

    protected WebDriver driver_;

    @Override
    public void ValidateParams() {
//        Validator.given(capsFile, "Caps file (-cf)").isSetThen()
//                .required(seleniumServerURL, "Selenium server (-se)")
//                .notAllowed(browser, "browser (-br)");

        Validator.given(sessionId, "Session id (-id)")
                .isSetThen()
                .notAllowed(browser, "Browser (-br)")
                .notAllowed(capsFile, "Caps file (-cf)");
    }

    @Override
    public void Init() throws IOException, URISyntaxException, ParserConfigurationException, SAXException {
        super.Init();

        driver_ = InitDriver(sessionId, browser, seleniumServerURL, prepareCapabilities());
    }

    protected WebDriver InitDriver(String sessionId, String browser, String serverUrl, MutableCapabilities caps) throws IOException {
        WebDriver driver = null;
        if (!Strings.isNullOrEmpty(sessionId)) {
            URL seleniumServer = new URL(Strings.isNullOrEmpty(serverUrl) ? DEFAULT_SERVER : serverUrl);
            driver = new AttachedWebDriver(seleniumServer, sessionId);
        } else if (!Strings.isNullOrEmpty(serverUrl))
            driver = prepRemoteDriver(new URL(serverUrl), caps);
        else
            driver = prepLocalDriver(browser, caps);

        if (implicitWait > 0)
            driver.manage().timeouts().implicitlyWait(implicitWait, TimeUnit.SECONDS);
        if (pageloadTimeout > 0)
            driver.manage().timeouts().pageLoadTimeout(pageloadTimeout, TimeUnit.SECONDS);

        return driver;
    }

    protected WebDriver prepLocalDriver(String browser, MutableCapabilities caps) {
        Browser safeBrowser = getSafeBrowser(browser);
        switch (safeBrowser) {
            case Chrome:
                ChromeOptions options = enhanceChromeCaps(caps); //In case the capabilities were read from cap file, we still might need adding some stability capabilities
                return new ChromeDriver(options);
            case Internetexplorer:
            case Ie:
                InternetExplorerOptions ioo = new InternetExplorerOptions(caps);
                return new InternetExplorerDriver(ioo);
            case Edge:
                EdgeOptions eo = new EdgeOptions();
                eo.merge(caps);
                return new EdgeDriver(eo);
            case Safari:
                SafariOptions so = new SafariOptions(caps);
                return new SafariDriver(so);
            case Firefox:
                FirefoxDriver driver = new FirefoxDriver();
                driver.setLogLevel(Level.OFF);
                return driver;
            default:
                return null;
        }
    }

    protected WebDriver prepRemoteDriver(URL serverUrl, MutableCapabilities caps) {
        return new RemoteWebDriver(serverUrl, caps);
    }

    private MutableCapabilities prepareCapabilities() throws IOException {
        Browser browser = getSafeBrowser(this.browser);

        if (!Strings.isNullOrEmpty(capsFile))
            return readCapabilities(capsFile);
        else
            return prepareCaps(browser);
    }

    private static MutableCapabilities readCapabilities(String capsfile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return new MutableCapabilities(
                (Map<String, ?>) mapper.readValue(
                        new File(capsfile),
                        new TypeReference<Map<String, Object>>() {
                        }));
    }

    private static Browser getSafeBrowser(String browser) throws IllegalArgumentException {
        try {
            if (Strings.isNullOrEmpty(browser)) return DEFAULT_BROWSER;
            return Browser.valueOf(StringUtils.capitalize(browser.toLowerCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Wrong browser argument");
        }
    }

    private static DesiredCapabilities prepareCaps(Browser browser) {
        switch (browser) {
            case Chrome:
                DesiredCapabilities caps = DesiredCapabilities.chrome();
                enhanceChromeCaps(caps);
                return caps;
            case Internetexplorer:
            case Ie:
                return DesiredCapabilities.internetExplorer();
            case Edge:
                return DesiredCapabilities.edge();
            case Safari:
                return DesiredCapabilities.safari();
            case Firefox:
                return DesiredCapabilities.firefox();
        }
        return new DesiredCapabilities();
    }

    private static ChromeOptions enhanceChromeCaps(MutableCapabilities caps) {
        ChromeOptions options = new ChromeOptions();
        options.merge(caps);

        if (options.getCapability(ChromeOptions.CAPABILITY) != null) {
            HashMap chromeOptions = (HashMap) options.getCapability(ChromeOptions.CAPABILITY);
            if (chromeOptions.get("mobileEmulation") != null)
                options.setExperimentalOption("mobileEmulation", chromeOptions.get("mobileEmulation"));
            if (chromeOptions.get("args") != null)
                options.addArguments((List<String>) chromeOptions.get("args"));
            if (chromeOptions.get("extensions") != null)
                options.addExtensions((List<File>) chromeOptions.get("extensions"));
        }
        return options;
    }

    private static void writeCapabilities() {
        DesiredCapabilities caps = new DesiredCapabilities("safari", "", Platform.ANY);
        caps.setCapability("platformName", "iOS");
        caps.setCapability("manufacturer", "Apple");
        caps.setCapability("model", "iPhone-5S");
        writeCapabilities(caps, new File("caps.json"));
    }

    public static void writeCapabilities(MutableCapabilities caps, File destination) {
        ObjectMapper mapper = new ObjectMapper();
        //mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        try {
            mapper.writeValue(destination, caps.asMap());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void TearDown() {
        if (driver_ != null && Strings.isNullOrEmpty(sessionId))
            driver_.quit();
    }
}

package com.ullink.slack.review;

import java.io.FileReader;
import java.net.Proxy;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ullink.slack.review.gerrit.ChangeInfoFormatter;
import com.ullink.slack.review.gerrit.GerritChangeInfoService;
import com.ullink.slack.review.gerrit.ReviewRequestCleanupTask;
import com.ullink.slack.review.gerrit.reviewrequests.ReviewRequestService;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;

public class Connector
{
    private static final String DEFAULT_PROPERTIES_FILE = "slack4gerrit.properties";
    public static Injector injector = null;
    private static ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws Exception
    {
        Properties parameters = new Properties();
        parameters.load(new FileReader(DEFAULT_PROPERTIES_FILE));
        if (!parameters.containsKey(Constants.GERRIT_URL))
        {
            throw new IllegalArgumentException("missing property '" + Constants.GERRIT_URL + "' in " + DEFAULT_PROPERTIES_FILE);
        }
        if (!parameters.containsKey(Constants.CHANGE_INFO_FORMATTER_CLASS))
        {
            throw new IllegalArgumentException("missing property '" + Constants.CHANGE_INFO_FORMATTER_CLASS + "' in " + DEFAULT_PROPERTIES_FILE);
        }
        injector = Guice.createInjector(new BaseModule(parameters));
        SlackSession session;
        if (parameters.containsKey(Constants.PROXY_HOST))
        {
            String proxyURL = parameters.getProperty(Constants.PROXY_HOST);
            int proxyPort = Integer.parseInt(parameters.getProperty(Constants.PROXY_PORT, "80"));
            SlackSessionFactory.SlackSessionFactoryBuilder slackSessionBuilder = SlackSessionFactory.getSlackSessionBuilder(parameters.getProperty(Constants.OAUTH_TOKEN), parameters.getProperty(Constants.APP_LEVEL_TOKEN));
            session = slackSessionBuilder.withLegacyMode(Boolean.FALSE).withProxy(Proxy.Type.HTTP, proxyURL, proxyPort).withAutoreconnectOnDisconnection(true).withConnectionHeartbeat(365, TimeUnit.DAYS).build();
        }
        else
        {
            SlackSessionFactory.SlackSessionFactoryBuilder slackSessionBuilder = SlackSessionFactory.getSlackSessionBuilder(parameters.getProperty(Constants.OAUTH_TOKEN), parameters.getProperty(Constants.APP_LEVEL_TOKEN));
            session = slackSessionBuilder.withLegacyMode(Boolean.FALSE).withAutoreconnectOnDisconnection(true).withConnectionHeartbeat(365, TimeUnit.DAYS).build();
        }
        ReviewRequestService reviewRequestService = Connector.injector.getProvider(ReviewRequestService.class).get();
        GerritChangeInfoService gerritChangeInfoService = Connector.injector.getProvider(GerritChangeInfoService.class).get();
        ChangeInfoFormatter gerritChangeDecorator = Connector.injector.getProvider(ChangeInfoFormatter.class).get();
        session.addMessagePostedListener(new ReviewMessageListener());
        scheduledExecutor.scheduleAtFixedRate(new ReviewRequestCleanupTask(reviewRequestService, gerritChangeInfoService, gerritChangeDecorator, session, scheduledExecutor), 1, 5, TimeUnit.MINUTES);
        session.connect();

        Thread.sleep(Long.MAX_VALUE);
    }
}

package com.adyen.mirakl.cucumber.stepdefs.helpers.restassured;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class RestAssuredAdyenApi {

    private static final Logger log = LoggerFactory.getLogger(RestAssuredAdyenApi.class);

    public Map<String, Object> getAdyenNotificationBody(String endpoint, String miraklShopId, String eventType, String verificationType) {
        ResponseBody body = getResponseBody(endpoint);
        List<String> check = body.jsonPath().get("body");
        if (!CollectionUtils.isEmpty(check)) {
            for (String list : check) {

                Map<String, Object> mapResult = new HashMap<>(new Gson().fromJson(list, new TypeToken<HashMap<String, Object>>() {
                }.getType()));

                final Map contentMap = (Map) mapResult.get("content");
                // if verificationType, accountHolderCode and verificationType have been provided
                // by cucumber and they match the notification in endpoint then return that notification
                // else if only accountHolderCode and eventType have been provided then return that notification
                if (contentMap.get("verificationType") != null && verificationType != null) {
                    if (contentMap.get("accountHolderCode").equals(miraklShopId)
                        && mapResult.get("eventType").equals(eventType)
                        && contentMap.get("verificationType").equals(verificationType)) {
                        log.info("found from url: {} : {}", endpoint, list);
                        return mapResult;
                    }
                } else {
                    if (contentMap.get("accountHolderCode").equals(miraklShopId) && mapResult.get("eventType").equals(eventType)) {
                        log.info("found from url: {} : {}", endpoint, list);
                        return mapResult;
                    }
                }
            }
        }
        return null;
    }

    public List<DocumentContext> getMultipleAdyenNotificationBodies(String endpoint, String miraklShopId, String eventType, String verificationType, List<String> shareholderCodes) {
        ResponseBody body = getResponseBody(endpoint);
        List<String> allNotifications = body.jsonPath().get("body");
        List<String> notifications = new LinkedList<>();
        // filter through all notifications and add all that match criteria to String List
        for (String notification : allNotifications) {
            Map contentMap = JsonPath.parse(notification).read("content");
            DocumentContext content = JsonPath.parse(contentMap);
            Assertions.assertThat(JsonPath.parse(notification).read("eventType").toString()).isNotEmpty();
            if (JsonPath.parse(notification).read("eventType").toString().equals(eventType) &&
                content.read("accountHolderCode").equals(miraklShopId) &&
                content.read("verificationType").equals(verificationType)) {

                notifications.add(notification);
            }
        }

        // filter through String List of notifications to see if shareholderCode matches
        // add all that match to list builder
        ImmutableList.Builder<DocumentContext> listBuilder = new ImmutableList.Builder<>();
        for (String notification : notifications) {
            DocumentContext parsedNotification = JsonPath.parse(notification);

            if (parsedNotification.read("content.shareholderCode") != null) {
                for (String shareholderCode : shareholderCodes) {
                    if (parsedNotification.read("content.shareholderCode").toString().equals(shareholderCode)) {
                        listBuilder.add(JsonPath.parse(notification));
                    }
                }
            }
        }
        return listBuilder.build();
    }

    public boolean endpointHasANotification(String endpoint) {
        return !"[]".equals(getResponseBody(endpoint).asString());
    }

    private ResponseBody getResponseBody(String endpoint) {
        ResponseBody body = RestAssured.get(endpoint).getBody();

        try {
            body.jsonPath().get("error");
        } catch (Exception e) {
            log.info("\n-------------------------------------------------------");
            log.info("\nget body response was: \n{}", body.prettyPrint());
            log.info("\n-------------------------------------------------------");
        }

        if (body.jsonPath().get("error").equals("Bin not found")) {
            throw new IllegalStateException(String.format("Bin no longer exists. Endpoint is showing: [%s]", body.jsonPath().get("error").toString()));
        }
        return body;
    }
}

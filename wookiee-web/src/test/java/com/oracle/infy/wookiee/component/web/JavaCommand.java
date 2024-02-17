package com.oracle.infy.wookiee.component.web;

import com.oracle.infy.wookiee.component.web.http.HttpCommand;
import com.oracle.infy.wookiee.component.web.http.HttpObjects;
import com.oracle.infy.wookiee.utils.ThreadUtil;
import scala.Enumeration;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.List;
import java.util.Map;

public class JavaCommand implements HttpCommand {
    ExecutionContext executionContext = ThreadUtil.createEC("java-endpoint");

    @Override
    public String path() {
        return "java";
    }

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public Enumeration.Value endpointType() {
        return HttpObjects.EndpointType$.MODULE$.EXTERNAL();
    }

    @Override
    public Future<HttpObjects.WookieeResponse> execute(HttpObjects.WookieeRequest args) {
        return Future.apply(() ->
                HttpObjects.WookieeResponse$.MODULE$.apply(
                        HttpObjects.Content$.MODULE$.apply("<?xml version=\"1.0\"?><!DOCTYPE cross-domain-policy SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\"><cross-domain-policy><site-control permitted-cross-domain-policies=\"master-only\"/><allow-access-from domain=\"*\" secure=\"false\"/><allow-http-request-headers-from domain=\"*\" headers=\"*\" secure=\"false\"/></cross-domain-policy>"),
                        HttpObjects.Headers$.MODULE$.withMappings(Map.of("Content-Type", List.of("text/xml; charset=UTF-8")))
                ), executionContext
        );
    }
}

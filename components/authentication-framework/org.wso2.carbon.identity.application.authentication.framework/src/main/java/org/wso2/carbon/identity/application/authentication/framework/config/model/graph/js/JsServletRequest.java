package org.wso2.carbon.identity.application.authentication.framework.config.model.graph.js;

import org.wso2.carbon.identity.application.authentication.framework.context.TransientObjectWrapper;

import javax.servlet.http.HttpServletRequest;

public interface JsServletRequest {
    TransientObjectWrapper<HttpServletRequest> getWrapped();
}

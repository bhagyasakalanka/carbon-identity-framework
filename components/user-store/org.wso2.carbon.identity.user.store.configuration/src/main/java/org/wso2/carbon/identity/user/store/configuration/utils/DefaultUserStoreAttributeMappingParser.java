/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.com).
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.user.store.configuration.utils;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.user.store.configuration.model.UserStoreAttributeDO;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.ATTRIBUTE_ID;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.CLAIM_CONFIG;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.CLAIM_URI;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.DIALECT;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.DIALECTS;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.DISPLAY_NAME;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.LOCAL_DIALECT_URL;

/**
 * Parser to read default user store mappings from claim-config.xml.
 */
public class DefaultUserStoreAttributeMappingParser {

    private static Map<String, UserStoreAttributeDO> defaultAttributes;
    private static final Log LOG = LogFactory.getLog(DefaultUserStoreAttributeMappingParser.class);

    private DefaultUserStoreAttributeMappingParser() {

        init();
    }

    private static final class ParserHolder {

        static final DefaultUserStoreAttributeMappingParser PARSER = new DefaultUserStoreAttributeMappingParser();
    }

    public static DefaultUserStoreAttributeMappingParser getInstance() {

        return ParserHolder.PARSER;
    }

    private static void init() {

        InputStream inStream = null;

        File attributeMappingXml = new File(CarbonUtils.getCarbonConfigDirPath(),
                CLAIM_CONFIG);
        try {
            if (attributeMappingXml.exists()) {

                inStream = new FileInputStream(attributeMappingXml);
            }

            if (inStream == null) {
                String message = String.format("Claim-config configuration is not found at: %s/%s",
                        CarbonUtils.getCarbonConfigDirPath(), CLAIM_CONFIG);
                throw new FileNotFoundException(message);
            }
            buildDefaultAttributeMapping(inStream);
        } catch (FileNotFoundException e) {
            LOG.error(String.format("Claim-config configuration is not found at: %s/%s",
                    CarbonUtils.getCarbonConfigDirPath(), CLAIM_CONFIG), e);
        }
    }

    private static void buildDefaultAttributeMapping(InputStream inStream) {

        StAXOMBuilder builder;
        OMElement localClaimElement = null;
        try {
            builder = new StAXOMBuilder(inStream);

            Iterator iterator = builder.getDocumentElement().
                    getFirstChildWithName(new QName(DIALECTS)).
                    getChildrenWithLocalName(DIALECT);
            if (iterator != null) {
                // Select local claim attributes.
                while (iterator.hasNext()) {
                    localClaimElement = (OMElement) iterator.next();
                    Iterator attributeIterator = localClaimElement.getAllAttributes();
                    if (attributeIterator != null) {
                        String attributeValue = ((OMAttribute) attributeIterator.next()).getAttributeValue();
                        if (attributeValue.equals(LOCAL_DIALECT_URL)) {
                            break;
                        }
                    }
                }
                if (localClaimElement == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Local claims cannot be found.");
                    }
                    return;
                }
                Iterator claimIterator = localClaimElement.getChildElements();

                defaultAttributes = new HashMap<>();
                while (claimIterator.hasNext()) {
                    OMElement claimElement = (OMElement) claimIterator.next();
                    Iterator attributeIterator = claimElement.getChildElements();
                    UserStoreAttributeDO userStoreAttributeDO = new UserStoreAttributeDO();
                    while (attributeIterator.hasNext()) {
                        OMElement attributes = (OMElement) attributeIterator.next();
                        String attributeQName = attributes.getQName().getLocalPart();
                        if (attributeQName.equals(DISPLAY_NAME)) {
                            userStoreAttributeDO.setDisplayName(attributes.getText());
                        }
                        if (attributeQName.equals(ATTRIBUTE_ID)) {
                            userStoreAttributeDO.setMappedAttribute(attributes.getText());
                        }
                        if (attributeQName.equals(CLAIM_URI)) {
                            userStoreAttributeDO.setClaimUri(attributes.getText());
                            userStoreAttributeDO.setClaimId(Base64.getEncoder().
                                    encodeToString(attributes.getText().getBytes(StandardCharsets.UTF_8)));
                        }
                        defaultAttributes.put(userStoreAttributeDO.getClaimId(), userStoreAttributeDO);
                    }
                }

            }
        } catch (XMLStreamException e) {
            LOG.error("Error occurred while reading the claim-config.xml file", e);
        } finally {
            try {
                inStream.close();
            } catch (IOException e) {
                LOG.error("Error occurred while closing input stream", e);
            }
        }
    }

    /**
     * Get default user store attributes.
     *
     * @return String defaultAttributes.
     */
    public Map<String, UserStoreAttributeDO> getDefaultAttributes() {

        return defaultAttributes;
    }

}

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

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.lang3.StringUtils;
import org.wso2.carbon.identity.user.store.configuration.model.ChangedUserStoreAttributeDO;
import org.wso2.carbon.identity.user.store.configuration.model.UserStoreAttributeDO;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.ATTRIBUTES_DIR;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.ATTRIBUTE_ID;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.CLAIM_URI;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.DISPLAY_NAME;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.OPERATION;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.USERSTORE_DIR;
import static org.wso2.carbon.identity.user.store.configuration.utils.UserStoreConfigurationConstant.USERSTORE_TYPE;

/**
 * Parser to read and build user store mappings attribute changes.
 */
public class UserStoreAttributeMappingParser {

    public Map<String, Map<String, ChangedUserStoreAttributeDO>> getUserStoresAttributeMappings()
            throws IdentityUserStoreServerException {

        Map<String, Map<String, ChangedUserStoreAttributeDO>> userStoreAttributeChanges = new HashMap<>();
        try {
            for (String fileName : getFileNames()) {
                readAttributeMappingFile(userStoreAttributeChanges, fileName);
            }
        } catch (IOException e) {
            throw new IdentityUserStoreServerException(
                    String.format("Error occurred while reading user store attribute mapping file names inside %s.",
                            getUserStoreAttributeMappingDirPath()), e);
        }
        return userStoreAttributeChanges;
    }

    private void readAttributeMappingFile(
            Map<String, Map<String, ChangedUserStoreAttributeDO>> userStoreAttributeChanges, String fileName)
            throws IdentityUserStoreServerException {

        File attributeMappingXml = new File(getUserStoreAttributeMappingDirPath(), fileName);
        if (!attributeMappingXml.exists()) {
            throw new IdentityUserStoreServerException(
                    String.format("%s file cannot be found at %s/%s.", fileName,
                            getUserStoreAttributeMappingDirPath(), fileName));
        }
        try (InputStream inStream = new FileInputStream(attributeMappingXml)) {
            buildAttributeMappings(inStream, userStoreAttributeChanges);
        } catch (IOException e) {
            throw new IdentityUserStoreServerException(
                    String.format("Error occurred while reading %s.", fileName, e));
        } catch (XMLStreamException e) {
            throw new IdentityUserStoreServerException("Error occurred while handling xml files.", e);
        }
    }

    private void buildAttributeMappings(InputStream inStream,
                                        Map<String, Map<String, ChangedUserStoreAttributeDO>> userStoreAttributeChanges)
            throws XMLStreamException {

        StAXOMBuilder builder = new StAXOMBuilder(inStream);
        Iterator iterator = builder.getDocumentElement().getChildElements();
        String userStoreType = builder.getDocumentElement().getAttributeValue(new QName(USERSTORE_TYPE));
        Map<String, ChangedUserStoreAttributeDO> attributeChangeDOMap = new HashMap<>();
        if (iterator == null) {
            return;
        }
        while (iterator.hasNext()) {
            OMElement attributeElement = (OMElement) iterator.next();
            Iterator attributeIterator = attributeElement.getChildElements();
            ChangedUserStoreAttributeDO changedUserStoreAttributeDO = new ChangedUserStoreAttributeDO();
            UserStoreAttributeDO userStoreAttributeDO = new UserStoreAttributeDO();
            if (attributeIterator == null) {
                continue;
            }
            while (attributeIterator.hasNext()) {
                OMElement attributes = (OMElement) attributeIterator.next();
                String attributeQName = attributes.getQName().getLocalPart();
                if (StringUtils.equalsIgnoreCase(OPERATION, attributeQName)) {
                    changedUserStoreAttributeDO.setOperation(attributes.getText());
                } else if (StringUtils.equalsIgnoreCase(ATTRIBUTE_ID, attributeQName)) {
                    userStoreAttributeDO.setMappedAttribute(attributes.getText());
                } else if (StringUtils.equalsIgnoreCase(CLAIM_URI, attributeQName)) {
                    userStoreAttributeDO.setClaimUri(attributes.getText());
                    userStoreAttributeDO.setClaimId(Base64.getUrlEncoder().withoutPadding().
                            encodeToString(attributes.getText().getBytes(StandardCharsets.UTF_8)));
                } else if (StringUtils.equalsIgnoreCase(DISPLAY_NAME, attributeQName)) {
                    userStoreAttributeDO.setDisplayName(attributes.getText());
                }
            }
            changedUserStoreAttributeDO.setUsAttributeDO(userStoreAttributeDO);
            attributeChangeDOMap.put(userStoreAttributeDO.getClaimId(), changedUserStoreAttributeDO);
        }
        userStoreAttributeChanges.put(userStoreType, attributeChangeDOMap);
    }

    private static String getUserStoreAttributeMappingDirPath() {

        return String.format("%s%s%s%s%s", CarbonUtils.getCarbonConfigDirPath(), File.separator, ATTRIBUTES_DIR,
                File.separator, USERSTORE_DIR);
    }

    /**
     * Read all file names inside conf/attributes/userstore directory.
     *
     * @return List of file names.
     * @throws IOException If error occurred while reading file names.
     */
    private static List<String> getFileNames() throws IOException {

        return Files.walk(Paths.get(getUserStoreAttributeMappingDirPath()))
                .filter(Files::isRegularFile)
                .map(Path::toFile).map(File::getName)
                .collect(Collectors.toList());
    }
}
